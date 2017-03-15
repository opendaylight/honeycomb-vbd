/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vbd.impl;

import java.util.List;
import java.util.concurrent.Executors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.MountPointService;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.TerminationPointVbridgeCfgAugment;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.TerminationPointVbridgeCfgAugmentBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.TopologyTypesVbridgeAugment;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.TopologyVbridgeCfgAugment;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.TopologyVbridgeCfgAugmentBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.network.topology.topology.VbridgeStartupConfigBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.network.topology.topology.node.termination.point._interface.type.cfg.VirtualDomainCarrierCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.network.topology.topology.node.termination.point._interface.type.cfg.virtual.domain.carrier._case.VirtualDomainCarrierBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.network.topology.topology.topology.types.VbridgeTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TpId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.TopologyTypes;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * Tip for the Virtual Bridge Domain implementation. This class is instantiated when the application is started
 * and {@link #close()}d when it is shut down.
 */
public final class VirtualBridgeDomainManager implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(VirtualBridgeDomainManager.class);
    private static final DataTreeIdentifier<VbridgeTopology> LISTEN_TREE =
            new DataTreeIdentifier<>(LogicalDatastoreType.CONFIGURATION,
                    InstanceIdentifier.builder(NetworkTopology.class).child(Topology.class).child(TopologyTypes.class)
                    .augmentation(TopologyTypesVbridgeAugment.class).child(VbridgeTopology.class).build());

    private static final String UNSPECIFIED_INIT_INTF_CONFIG = "unspecified";
    private final ListenerRegistration<TopologyMonitor> reg;
    
    private boolean closed;

    private VirtualBridgeDomainManager(final ListenerRegistration<TopologyMonitor> reg) {
        this.reg = Preconditions.checkNotNull(reg);
    }

    public static VirtualBridgeDomainManager create(@Nonnull final DataBroker dataBroker,
            @Nonnull MountPointService mountService, @Nullable String virtualDomainInterfaces) {
        final ListenerRegistration<TopologyMonitor> reg =
                dataBroker.registerDataTreeChangeListener(LISTEN_TREE, new TopologyMonitor(dataBroker, mountService));
        if (!Strings.isNullOrEmpty(virtualDomainInterfaces)
                && !virtualDomainInterfaces.equals(UNSPECIFIED_INIT_INTF_CONFIG)) {
            Executors.newSingleThreadExecutor().submit(() -> {
                for (int i = 0; i < 15; i++) {
                    WriteTransaction wTx = dataBroker.newWriteOnlyTransaction();
                    try {
                        Thread.sleep(600);
                        writeStartupConfig(wTx, virtualDomainInterfaces);
                        wTx.submit().get();
                        break;
                    } catch (Exception e) {
                        LOG.warn("Failed to write startup data to operational DS. Attempt num {}, {} left.", i, 13 - i);
                        try {
                            wTx.cancel();
                        } finally {
                            wTx = null;
                        }
                    }
                }
            });
        }
        return new VirtualBridgeDomainManager(reg);
    }

    public static void writeStartupConfig(@Nonnull WriteTransaction wTx, @Nonnull String virtualDomainInterfaces) {
        Topology confTopology = new TopologyBuilder().setTopologyId(VbdUtil.STARTUP_CONFIG_TOPOLOGY)
            .addAugmentation(TopologyVbridgeCfgAugment.class, getTopologyAug().build())
            .build();
        wTx.put(LogicalDatastoreType.OPERATIONAL, VbdUtil.topologyIid(VbdUtil.STARTUP_CONFIG_TOPOLOGY).build(),
                confTopology, true);
        for (String intfOnNode : Sets.newConcurrentHashSet(Splitter.on(",").split(virtualDomainInterfaces))) {
            List<String> entries = Lists.newArrayList(Splitter.on(":").split(intfOnNode));
            if (entries.size() != 2) {
                LOG.warn("Cannot resolve {} initial configuration for interface.", intfOnNode);
                continue;
            }
            NodeId nodeId = new NodeId(entries.get(0));
            TpId tpId = new TpId(entries.get(1));
            LOG.debug("Writing startup config for interface " + tpId + " at " + nodeId);
            wTx.put(LogicalDatastoreType.OPERATIONAL,
                    VbdUtil.terminationPointIid(VbdUtil.STARTUP_CONFIG_TOPOLOGY, nodeId, tpId)
                        .augmentation(TerminationPointVbridgeCfgAugment.class).build(), getTpAug().build(), true);
        }
    }

    private static TerminationPointVbridgeCfgAugmentBuilder getTpAug() {
        return new TerminationPointVbridgeCfgAugmentBuilder().setInterfaceTypeCfg(new VirtualDomainCarrierCaseBuilder().build());
    }

    private static TopologyVbridgeCfgAugmentBuilder getTopologyAug() {
        return new TopologyVbridgeCfgAugmentBuilder().setVbridgeStartupConfig(new VbridgeStartupConfigBuilder().build());
    }

    @Override
    public void close() {
        if (!closed) {
            LOG.debug("Virtual Bridge Domain manager shut down started");

            final TopologyMonitor monitor = reg.getInstance();
            reg.close();
            LOG.debug("Topology monitor {} unregistered", monitor);
            monitor.close();

            closed = true;
            LOG.debug("Virtual Bridge Domain manager shut down completed");
        }
    }
}
