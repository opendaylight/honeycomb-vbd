/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vbd.impl;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.controller.md.sal.binding.api.BindingTransactionChain;
import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.binding.api.MountPointService;
import org.opendaylight.controller.md.sal.common.api.data.AsyncTransaction;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChain;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChainListener;
import org.opendaylight.vbd.api.VxlanTunnelIdAllocator;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.status.rev161005.BridgeDomainStatusFields.BridgeDomainStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.network.topology.topology.topology.types.VbridgeTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class responsible for monitoring /network-topology/topology and activating a {@link VbdBridgeDomain} when a particular
 * topology is marked as a bridge domain.
 */
final class TopologyMonitor implements ClusteredDataTreeChangeListener<VbridgeTopology>, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(TopologyMonitor.class);
    private static final VxlanTunnelIdAllocator tunnelIdAllocator = new VxlanTunnelIdAllocatorImpl();
    @GuardedBy("this")
    private final Map<TopologyKey, VbdBridgeDomain> domains = new HashMap<>();
    private final DataBroker dataBroker;
    private final MountPointService mountService;
    // Number of attempts BD can be restarted during creation
    @SuppressWarnings("FieldCanBeLocal")
    private final byte BD_RESTART_COUNTER = 5;

    TopologyMonitor(final DataBroker dataBroker, final MountPointService mountService) {
        this.dataBroker = Preconditions.checkNotNull(dataBroker);
        this.mountService = Preconditions.checkNotNull(mountService);
    }

    @Override
    public synchronized void onDataTreeChanged(@Nonnull final Collection<DataTreeModification<VbridgeTopology>> changes) {
        for (DataTreeModification<VbridgeTopology> c : changes) {
            @SuppressWarnings("unchecked")
            final KeyedInstanceIdentifier<Topology, TopologyKey> topology =
                    (KeyedInstanceIdentifier<Topology, TopologyKey>) c.getRootPath().getRootIdentifier()
                            .firstIdentifierOf(Topology.class);

            Preconditions.checkArgument(!topology.isWildcarded(), "Wildcard topology %s is not supported", topology);

            final DataObjectModification<VbridgeTopology> mod = c.getRootNode();
            ListenableFuture<Void> processionState = Futures.immediateFuture(null);
            switch (mod.getModificationType()) {
                case DELETE:
                    LOG.debug("Topology {} removed", PPrint.topology(topology));
                    processionState = stopDomain(topology);
                    break;
                case WRITE:
                    LOG.debug("Topology {} added", PPrint.topology(topology));
                    processionState = startDomain(topology, BD_RESTART_COUNTER);
                    break;
                default:
                    LOG.warn("Ignoring unhandled modification type {}", mod.getModificationType());
                    break;
            }
            Futures.addCallback(processionState, new FutureCallback<Void>() {
                @Override
                public void onSuccess(@Nullable Void aVoid) {
                    LOG.info("VBridge topology {} procession completed", topology.getKey());
                }

                @Override
                public void onFailure(@Nullable Throwable throwable) {
                    LOG.warn("VBridge topology {} procession failed", topology.getKey());
                }
            });
        }
    }

    private synchronized void completeDomain(final KeyedInstanceIdentifier<Topology, TopologyKey> topology) {
        LOG.debug("Bridge domain for {} completed operation", PPrint.topology(topology));
        domains.remove(topology.getKey());

        synchronized (domains) {
            domains.notify();
        }
    }

    private synchronized void restartDomain(final KeyedInstanceIdentifier<Topology, TopologyKey> topology, byte counter) {
        try {
            LOG.warn("Restarting domain {}", PPrint.topology(topology));
            final VbdBridgeDomain prev = domains.get(topology.getKey());
            if (prev != null) {
                prev.forceStop();
            }
            Thread.sleep(4000); // Wait some time till the next try
            startDomain(topology, --counter);
        } catch (InterruptedException e) {
            LOG.warn("Thread interrupted to ... ", e);
        }
    }

    @GuardedBy("this")
    private ListenableFuture<Void> startDomain(final KeyedInstanceIdentifier<Topology, TopologyKey> topology, byte counter) {
        VbdUtil.updateStatus(dataBroker, PPrint.topology(topology), BridgeDomainStatus.Starting);
        LOG.debug("Starting bridge domain for {}", PPrint.topology(topology));
        final VbdBridgeDomain prev = domains.get(topology.getKey());
        if (prev != null) {
            LOG.warn("Bridge domain {} for {} already started", prev, PPrint.topology(topology));
            return VbdUtil.updateStatus(dataBroker, PPrint.topology(topology), BridgeDomainStatus.Started);
        }
        final BindingTransactionChain chain = dataBroker.createTransactionChain(new TransactionChainListener() {
            @Override
            public void onTransactionChainSuccessful(final TransactionChain<?, ?> chain) {
                completeDomain(topology);
            }

            @Override
            public void onTransactionChainFailed(final TransactionChain<?, ?> chain,
                                                 final AsyncTransaction<?, ?> transaction, final Throwable cause) {
                LOG.warn("Bridge domain for topology {} failed, restarting it. Cause: {}", PPrint.topology(topology),
                        cause.getMessage());
                if (counter > 0) {
                    restartDomain(topology, counter);
                } else {
                    LOG.warn("Bridge domain {} cannot be created, maximum number of attempts reached",
                            PPrint.topology(topology));
                }
            }
        });
        VbdBridgeDomain domain;
        try {
            domain = VbdBridgeDomain.create(dataBroker, mountService, topology, chain, tunnelIdAllocator);
            domains.put(topology.getKey(), domain);
            LOG.debug("Bridge domain {} for {} started", domain, PPrint.topology(topology));
            return VbdUtil.updateStatus(dataBroker, PPrint.topology(topology), BridgeDomainStatus.Started);
        } catch (Exception e) {
            LOG.warn("VBD failed to create/startProbing bridge domain {}", PPrint.topology(topology), e.getMessage());
            return VbdUtil.updateStatus(dataBroker, PPrint.topology(topology), BridgeDomainStatus.Failed);
        }
    }

    @GuardedBy("this")
    private ListenableFuture<Void> stopDomain(final KeyedInstanceIdentifier<Topology, TopologyKey> topology) {
        final VbdBridgeDomain domain = domains.remove(topology.getKey());
        if (domain == null) {
            LOG.warn("Bridge domain for {} not present", PPrint.topology(topology));
            return Futures.immediateFuture(null);
        }

        domain.stop();
        return VbdUtil.updateStatus(dataBroker, PPrint.topology(topology), BridgeDomainStatus.Stopped);
    }

    @Override
    public synchronized void close() {
        LOG.debug("Topology monitor {} shut down started", this);

        for (Entry<TopologyKey, VbdBridgeDomain> e : domains.entrySet()) {
            LOG.debug("Shutting down bridge domain {} (key {})", e.getValue(), e.getKey());
            e.getValue().stop();
        }

        while (!domains.isEmpty()) {
            LOG.debug("Waiting for domains for {} to complete", domains.keySet());
            synchronized (domains) {
                try {
                    domains.wait();
                } catch (InterruptedException e) {
                    LOG.warn("Interrupted while waiting for domain shutdown, {} have not completed yet",
                            domains.keySet(), e);
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        LOG.debug("Topology monitor {} shut down completed", this);
    }

    private static class VxlanTunnelIdAllocatorImpl implements VxlanTunnelIdAllocator {

        private final Map<KeyedInstanceIdentifier<Node, NodeKey>, Integer> vppIIToNextTunnelId;

        VxlanTunnelIdAllocatorImpl() {
            vppIIToNextTunnelId = new HashMap<>();
        }

        @Override
        public synchronized Integer nextIdFor(final KeyedInstanceIdentifier<Node, NodeKey> iiToVPP) {
            if (vppIIToNextTunnelId.containsKey(iiToVPP)) {
                final int value = vppIIToNextTunnelId.get(iiToVPP);
                vppIIToNextTunnelId.put(iiToVPP, value + 1);
                return value + 1;
            } else {
                vppIIToNextTunnelId.put(iiToVPP, 0);
                return 0;
            }
        }
    }
}
