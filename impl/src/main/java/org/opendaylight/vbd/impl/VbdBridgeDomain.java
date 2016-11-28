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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.controller.md.sal.binding.api.BindingTransactionChain;
import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification.ModificationType;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.binding.api.MountPointService;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.vbd.api.VxlanTunnelIdAllocator;
import org.opendaylight.yang.gen.v1.urn.ieee.params.xml.ns.yang.dot1q.types.rev150626.Dot1qVlanId;
import org.opendaylight.yang.gen.v1.urn.ieee.params.xml.ns.yang.dot1q.types.rev150626.SVlan;
import org.opendaylight.yang.gen.v1.urn.ieee.params.xml.ns.yang.dot1q.types.rev150626.dot1q.tag.or.any.Dot1qTag;
import org.opendaylight.yang.gen.v1.urn.ieee.params.xml.ns.yang.dot1q.types.rev150626.dot1q.tag.or.any.Dot1qTagBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4AddressNoZone;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.VlanId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.external.reference.rev160129.ExternalReference;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev161214.VxlanVni;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev161214.l2.base.attributes.interconnection.BridgeBasedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.LinkVbridgeAugment;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.LinkVbridgeAugmentBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.NodeVbridgeAugment;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.TerminationPointVbridgeAugment;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.TerminationPointVbridgeAugmentBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.TopologyTypesVbridgeAugment;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.TopologyTypesVbridgeAugmentBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.TopologyVbridgeAugment;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.TunnelType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.network.topology.topology.TunnelParameters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.network.topology.topology.node.BridgeMember;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.network.topology.topology.node.BridgeMemberBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.network.topology.topology.node.termination.point._interface.type.TunnelInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.network.topology.topology.topology.types.VbridgeTopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.tunnel.vlan.rev160429.NodeVbridgeVlanAugment;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.tunnel.vlan.rev160429.TunnelTypeVlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.tunnel.vlan.rev160429.network.topology.topology.tunnel.parameters.VlanNetworkParameters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.tunnel.vxlan.rev160429.TunnelTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.tunnel.vxlan.rev160429.network.topology.topology.tunnel.parameters.VxlanTunnelParameters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev161214.SubinterfaceAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev161214.VlanType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev161214.interfaces._interface.SubInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev161214.interfaces._interface.sub.interfaces.SubInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev161214.interfaces._interface.sub.interfaces.SubInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev161214.interfaces._interface.sub.interfaces.SubInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev161214.match.attributes.match.type.VlanTagged;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev161214.match.attributes.match.type.VlanTaggedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev161214.sub._interface.base.attributes.L2;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev161214.sub._interface.base.attributes.L2Builder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev161214.sub._interface.base.attributes.Match;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev161214.sub._interface.base.attributes.MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev161214.sub._interface.base.attributes.Tags;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev161214.sub._interface.base.attributes.TagsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev161214.sub._interface.base.attributes.l2.RewriteBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev161214.sub._interface.base.attributes.tags.TagBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.LinkId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TpId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.link.attributes.Destination;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.link.attributes.DestinationBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.link.attributes.Source;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.link.attributes.SourceBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.LinkBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.LinkKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.TopologyTypes;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.TopologyTypesBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPointBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.node.attributes.SupportingNode;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of a single Virtual Bridge Domain. It is bound to a particular network topology instance, manages
 * bridge members and projects state into the operational data store.
 */
final class VbdBridgeDomain implements ClusteredDataTreeChangeListener<Topology> {
    private static final Logger LOG = LoggerFactory.getLogger(VbdBridgeDomain.class);

    private static final int SOURCE_VPP_INDEX = 0;
    private static final int DESTINATION_VPP_INDEX = 1;
    private static final short VLAN_TAG_INDEX_ZERO = 0;
    private static final int MAXLEN = 8;
    private final DataBroker dataBroker;
    private final KeyedInstanceIdentifier<Topology, TopologyKey> topology;
    @GuardedBy("this")

    private final BindingTransactionChain chain;
    private final ListenerRegistration<?> reg;
    private final MountPointService mountService;
    private final VppModifier vppModifier;
    private final VxlanTunnelIdAllocator tunnelIdAllocator;
    private TopologyVbridgeAugment config;
    private final String bridgeDomainName;
    private final String iiBridgeDomainOnVPPRest;
    @SuppressWarnings("FieldCanBeLocal")
    private Multimap<NodeId, KeyedInstanceIdentifier<Node, NodeKey>> nodesToVpps = ArrayListMultimap.create();

    private VbdBridgeDomain(final DataBroker dataBroker, final MountPointService mountService, final KeyedInstanceIdentifier<Topology, TopologyKey> topology,
                            final BindingTransactionChain chain, VxlanTunnelIdAllocator tunnelIdAllocator) throws Exception {
        this.dataBroker = Preconditions.checkNotNull(dataBroker);
        this.bridgeDomainName = topology.getKey().getTopologyId().getValue();
        this.vppModifier = new VppModifier(dataBroker, mountService, bridgeDomainName, this);

        this.topology = Preconditions.checkNotNull(topology);
        this.chain = Preconditions.checkNotNull(chain);
        this.mountService = mountService;
        this.tunnelIdAllocator = tunnelIdAllocator;

        this.iiBridgeDomainOnVPPRest = provideIIBrdigeDomainOnVPPRest();

        wipeOperationalState(topology, chain);
        createFreshOperationalState(topology, chain);
        readParams(topology, chain);

        reg = dataBroker.registerDataTreeChangeListener(
                new DataTreeIdentifier<>(LogicalDatastoreType.CONFIGURATION, topology), this);
    }

    private String provideIIBrdigeDomainOnVPPRest() {
        final StringBuilder strBuilder = new StringBuilder();
        strBuilder.append("v3po:vpp/bridge-domains/bridge-domain/");
        strBuilder.append(bridgeDomainName);
        return strBuilder.toString();
    }

    private ListenableFuture<Void> wipeOperationalState(final KeyedInstanceIdentifier<Topology, TopologyKey> topology, final BindingTransactionChain chain) {
        LOG.info("Wiping operational state of {}", PPrint.topology(topology));

        final WriteTransaction tx = chain.newWriteOnlyTransaction();
        tx.delete(LogicalDatastoreType.OPERATIONAL, topology);
        return tx.submit();
    }

    private TopologyTypesVbridgeAugment createVbridgeTopologyType() {
        final TopologyTypesVbridgeAugmentBuilder bld = new TopologyTypesVbridgeAugmentBuilder();
        bld.setVbridgeTopology(new VbridgeTopologyBuilder().build());
        return bld.build();
    }

    private Topology buildFreshTopology(final KeyedInstanceIdentifier<Topology, TopologyKey> topoIID) {
        final TopologyTypes topoType = new TopologyTypesBuilder()
                .addAugmentation(TopologyTypesVbridgeAugment.class, createVbridgeTopologyType()).build();

        final TopologyBuilder tb = new TopologyBuilder();

        tb.setKey(topoIID.getKey())
                .setTopologyId(topoIID.getKey().getTopologyId())
                .setTopologyTypes(topoType);

        return tb.build();
    }

    private void createFreshOperationalState(final KeyedInstanceIdentifier<Topology, TopologyKey> topology, final BindingTransactionChain chain) {
        LOG.info("Creating fresh operational state for {}", PPrint.topology(topology));

        final WriteTransaction tx = chain.newWriteOnlyTransaction();
        tx.put(LogicalDatastoreType.OPERATIONAL, topology, buildFreshTopology(topology), true);
        tx.submit();
    }

    private static Void printVbridgeParams(final Topology t) {
        TopologyVbridgeAugment t2 = t.getAugmentation(TopologyVbridgeAugment.class);
        LOG.debug("Bridge Domain parameters:");
        LOG.debug("tunnelType: {}", t2.getTunnelType().getCanonicalName());
        LOG.debug("isFlood: {}", t2.isFlood());
        LOG.debug("isArpTermination: {}", t2.isArpTermination());
        LOG.debug("isForward: {}", t2.isForward());
        LOG.debug("isLearn: {}", t2.isLearn());
        LOG.debug("isUnknownUnicastFlood: {}", t2.isUnknownUnicastFlood());

        if (t2.getTunnelType().equals(TunnelTypeVxlan.class)) {
            final VxlanTunnelParameters vxlanTunnelParams =  (VxlanTunnelParameters) t2.getTunnelParameters();

            if (vxlanTunnelParams == null) {
                LOG.warn("Vxlan type topology was created but vxlan tunnel parameters is null!");
                return null;
            }

            final VxlanVni vni = vxlanTunnelParams.getVni();

            if (vni == null) {
                LOG.warn("Vxlan type topology was created but VNI parameter is null!");
                return null;
            }

            LOG.debug("vxlan vni: {}", vni.getValue());
        } else if (t2.getTunnelType().equals(TunnelTypeVlan.class)) {
            final VlanNetworkParameters vlanNetworkParameters = (VlanNetworkParameters) t2.getTunnelParameters();
            LOG.debug("vlan-type: {} vlan-id: {}", vlanNetworkParameters.getVlanId(), vlanNetworkParameters.getVlanType());
        }

        return null;
    }

    private void readParams(final KeyedInstanceIdentifier<Topology, TopologyKey> topology, final BindingTransactionChain chain) {
        final ReadOnlyTransaction tx = chain.newReadOnlyTransaction();
        Futures.addCallback(tx.read(LogicalDatastoreType.CONFIGURATION, topology), new FutureCallback<Optional<Topology>>() {
            @Override
            public void onSuccess(@Nullable Optional<Topology> result) {
                if (result != null) {
                    if (result.isPresent()) {
                        printVbridgeParams(result.get());
                    }
                }
            }

            @Override
            public void onFailure(@Nonnull Throwable t) {
                LOG.warn("Can't read Bridge domain parameters!", t);
            }
        });
    }

    @Nonnull
    static VbdBridgeDomain create(final DataBroker dataBroker, final MountPointService mountService,
                                  final KeyedInstanceIdentifier<Topology, TopologyKey> topology,
                                  final BindingTransactionChain chain,
                                  final VxlanTunnelIdAllocator tunnelIdAllocator) throws Exception {
        return new VbdBridgeDomain(dataBroker, mountService, topology, chain, tunnelIdAllocator);
    }

    synchronized void forceStop() {
        // TODO better be to return future
        LOG.debug("Bridge domain {} for {} going down", this, PPrint.topology(topology));
        reg.close();
        chain.close();
        LOG.info("Bridge domain {} for {} is down", this, PPrint.topology(topology));
    }

    synchronized void stop() {
        LOG.debug("Bridge domain {} for {} shutting down", this, PPrint.topology(topology));

        wipeOperationalState(topology, chain);
        chain.close();
    }

    private void deleteBridgeDomain() {
        LOG.debug("Deleting entire bridge domain {}", bridgeDomainName);
        final Collection<KeyedInstanceIdentifier<Node, NodeKey>> vppNodes = nodesToVpps.values();
        vppNodes.forEach(vppModifier::deleteBridgeDomainFromVppNode);
        nodesToVpps.clear();
    }

    private ListenableFuture<List<InstanceIdentifier<Link>>> findPrunableLinks(final KeyedInstanceIdentifier<Node, NodeKey> vbdNode) {
        LOG.debug("Finding prunable links for node {}", PPrint.node(vbdNode));

        final NodeId deletedNodeId = vbdNode.getKey().getNodeId();

        // read the topology to find the links
        final ReadOnlyTransaction rTx = chain.newReadOnlyTransaction();
        return Futures.transform(rTx.read(LogicalDatastoreType.OPERATIONAL, topology), new AsyncFunction<Optional<Topology>, List<InstanceIdentifier<Link>>>() {
            @Override
            public ListenableFuture<List<InstanceIdentifier<Link>>> apply(@Nonnull Optional<Topology> result) throws Exception {
                final List<InstanceIdentifier<Link>> prunableLinks = new ArrayList<>();

                if (result.isPresent()) {
                    final List<Link> links = result.get().getLink();

                    for (final Link link : links) {
                        // check if this link's source or destination matches the deleted node
                        final Source src = link.getSource();
                        final Destination dst = link.getDestination();
                        if (src.getSourceNode().equals(deletedNodeId)) {
                            LOG.debug("Link {} src matches deleted node id {}, adding to prunable list", link.getLinkId(), deletedNodeId);
                            final InstanceIdentifier<Link> linkIID = topology.child(Link.class, link.getKey());
                            prunableLinks.add(linkIID);
                        } else if (dst.getDestNode().equals(deletedNodeId)) {
                            LOG.debug("Link {} dst matches deleted node id {}, adding to prunable list", link.getLinkId(), deletedNodeId);
                            final InstanceIdentifier<Link> linkIID = topology.child(Link.class, link.getKey());
                            prunableLinks.add(linkIID);
                        }
                    }
                } else {
                    // result is null or not present
                    LOG.warn("Tried to read virtual bridge topology {}, but got null or absent optional!", PPrint.topology(topology));
                }

                return Futures.immediateFuture(prunableLinks);
            }
        });
    }

    private void pruneLinks(final KeyedInstanceIdentifier<Node, NodeKey> vbdNode) {
        LOG.debug("Pruning links to/from node {}", PPrint.node(vbdNode));
        final ListenableFuture<List<InstanceIdentifier<Link>>> prunableLinksFuture = findPrunableLinks(vbdNode);

        Futures.addCallback(prunableLinksFuture, new FutureCallback<List<InstanceIdentifier<Link>>>() {
            @Override
            public void onSuccess(@Nullable List<InstanceIdentifier<Link>> result) {
                if (result == null) {
                    LOG.warn("Got null result when finding prunable links for node {} on topology {}", PPrint.node(vbdNode), PPrint.topology(topology));
                    return;
                }

                for (final InstanceIdentifier<Link> linkIID : result) {
                    final WriteTransaction wTx = chain.newWriteOnlyTransaction();
                    wTx.delete(LogicalDatastoreType.OPERATIONAL, linkIID);
                    Futures.addCallback(wTx.submit(), new FutureCallback<Void>() {
                        @Override
                        public void onSuccess(@Nullable Void result) {
                            LOG.debug("Successfully deleted prunable link {} for node {} on vbd topology {}", linkIID, PPrint.node(vbdNode), PPrint.topology(topology));
                        }

                        @Override
                        public void onFailure(@Nullable Throwable t) {
                            LOG.warn("Failed to delete prunable link {} for node {} on vbd topology {}", linkIID, PPrint.node(vbdNode), PPrint.topology(topology), t);
                        }
                    });
                }
            }

            @Override
            public void onFailure(@Nullable Throwable t) {
                LOG.warn("Failed to get prunable links for vbd topology {}", PPrint.topology(topology), t);
            }
        });
    }

    private void removeNodeFromBridgeDomain(final KeyedInstanceIdentifier<Node, NodeKey> vppNode, final KeyedInstanceIdentifier<Node, NodeKey> backingNode) {
        LOG.debug("Removing node {} from bridge domain {}", PPrint.node(vppNode), bridgeDomainName);

        try {
            final Optional<ListenableFuture<Void>> deleteFutureOp = vppModifier.deleteBridgeDomainFromVppNode(vppNode);
            if (deleteFutureOp.isPresent()) {
                deleteFutureOp.get().get();
            }
        } catch (final ExecutionException e) {
            LOG.warn("Got exception while deleting bridge domain from vpp {} for topology {}", PPrint.node(vppNode), PPrint.topology(topology), e);
        } catch (final InterruptedException e) {
            LOG.info("Interrupted while deleting bridge domain from vpp {} for topology {}", PPrint.node(vppNode), PPrint.topology(topology), e);
            Thread.currentThread().interrupt();
        }

        pruneLinks(backingNode);

        // remove this node from vbd operational topology
        final WriteTransaction wTx3 = chain.newWriteOnlyTransaction();
        wTx3.delete(LogicalDatastoreType.OPERATIONAL, backingNode);

        Futures.addCallback(wTx3.submit(), new FutureCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void result) {
                LOG.debug("Removed backing node {} from virtual bridge operational topology {}", backingNode.toString(), bridgeDomainName);
            }

            @Override
            public void onFailure(@Nonnull Throwable t) {
                LOG.warn("Failed to delete node {} from virtual bridge operational topology {}", backingNode.toString(), bridgeDomainName, t);
            }
        });

        nodesToVpps.removeAll(vppNode);
    }

    @Override
    public synchronized void onDataTreeChanged(@Nonnull final Collection<DataTreeModification<Topology>> changes) {
        for (DataTreeModification<Topology> change : changes) {
            LOG.debug("Domain {} for {} processing change {}", this.bridgeDomainName, PPrint.topology(topology), change.getClass());
            final DataObjectModification<Topology> mod = change.getRootNode();
            switch (mod.getModificationType()) {
                case DELETE:
                    LOG.debug("Topology {} deleted, expecting shutdown", PPrint.topology(topology));
                    deleteBridgeDomain();
                    break;
                case SUBTREE_MODIFIED:
                    // First check if the configuration has changed
                    final DataObjectModification<TopologyVbridgeAugment> newConfig = mod.getModifiedAugmentation(TopologyVbridgeAugment.class);
                    if (newConfig != null) {
                        if (newConfig.getModificationType() != ModificationType.DELETE) {
                            LOG.debug("Topology {} modified configuration {}", PPrint.topology(topology), newConfig);
                            updateConfiguration(newConfig);
                        } else {
                            // FIXME: okay, what can we do about this one?
                            LOG.error("Topology {} configuration deleted, good luck!", PPrint.topology(topology));
                        }
                    }

                    handleModifiedChildren(mod.getModifiedChildren());

                    break;
                case WRITE:
                    final Topology data = mod.getDataAfter();

                    // Read configuration
                    final TopologyVbridgeAugment vbdConfig = data.getAugmentation(TopologyVbridgeAugment.class);
                    vppModifier.setConfig(vbdConfig);
                    if (vbdConfig != null) {
                        setConfiguration(vbdConfig);
                    } else {
                        LOG.error("Topology {} has no configuration, good luck!", PPrint.topology(topology));
                    }

                    // handle any nodes which were written with the new topology
                    handleModifiedChildren(mod.getModifiedChildren());

                    break;
                default:
                    LOG.warn("Unhandled topology modification {}", mod);
                    break;
            }
        }
    }

    private void handleModifiedChildren(final Collection<DataObjectModification<? extends DataObject>> children) {
        for (final DataObjectModification<? extends DataObject> child : children) {
            LOG.debug("Topology {} modified child {}", PPrint.topology(topology), child);

            if (Node.class.isAssignableFrom(child.getDataType())) {
                modifyNode((DataObjectModification<Node>) child);
            }
        }
    }

    private void modifyNode(final DataObjectModification<Node> nodeMod) {
        switch (nodeMod.getModificationType()) {
            case DELETE:
                LOG.debug("Topology {} node {} deleted", PPrint.topology(topology), nodeMod.getIdentifier());
                final Node deletedNode = nodeMod.getDataBefore();
                if (deletedNode != null) {
                    final KeyedInstanceIdentifier<Node, NodeKey> vppIID = nodesToVpps.get(deletedNode.getNodeId()).iterator().next();
                    final KeyedInstanceIdentifier<Node, NodeKey> backingNodeIID = topology.child(Node.class, deletedNode.getKey());
                    LOG.debug("Removing node from BD. Node: {}. backingNODE: {}", vppIID, backingNodeIID);
                    removeNodeFromBridgeDomain(vppIID, backingNodeIID);
                    if (config.getTunnelType().equals(TunnelTypeVxlan.class)) {
                        removeVxlanInterfaces(deletedNode.getNodeId());
                    }
                } else {
                    LOG.warn("Got null data before node when attempting to delete bridge domain {}", bridgeDomainName);
                }
                break;
            case SUBTREE_MODIFIED:
                LOG.debug("Topology {} node {} modified", PPrint.topology(topology), nodeMod.getIdentifier());
                for (DataObjectModification<? extends DataObject>  nodeChild : nodeMod.getModifiedChildren()) {
                    if (TerminationPoint.class.isAssignableFrom(nodeChild.getDataType())) {
                        modifyTerminationPoint((DataObjectModification<TerminationPoint>) nodeChild,nodeMod.getDataAfter().getNodeId());
                    }
                }
                break;
            case WRITE:
                LOG.debug("Topology {} node {} created", PPrint.topology(topology), nodeMod.getIdentifier());
                final int numberVppsBeforeAddition = nodesToVpps.keySet().size();
                final Node newNode = nodeMod.getDataAfter();
                if (newNode == null) {
                    LOG.warn("Node {} is null", nodeMod.getIdentifier());
                    return;
                }
                try {
                    createNode(newNode).get();
                } catch (InterruptedException | ExecutionException e) {
                    LOG.warn("Bridge domain {} was not created on node {}. Further processing is cancelled.",
                            java.util.Optional.ofNullable(topology)
                                .map(t -> t.firstKeyOf(Topology.class))
                                .map(TopologyKey::getTopologyId)
                                .map(Uri::getValue),
                            newNode.getNodeId().getValue(), e);
                    return;
                }
                if (config.getTunnelType().equals(TunnelTypeVxlan.class)) {
                    final int numberVppsAfterAddition = nodesToVpps.keySet().size();
                    if ((numberVppsBeforeAddition <= numberVppsAfterAddition) && (numberVppsBeforeAddition >= 1)) {
                        addVxlanTunnel(newNode.getNodeId());
                    }
                } else if (config.getTunnelType().equals(TunnelTypeVlan.class)) {
                    final NodeVbridgeVlanAugment vlanAug = newNode.getAugmentation(NodeVbridgeVlanAugment.class);
                    addVlanSubInterface(newNode.getNodeId(), vlanAug.getSuperInterface());
                } else {
                    LOG.warn("Unknown tunnel type {}", config.getTunnelType());
                }
                break;
            default:
                LOG.warn("Unhandled node modification {} in topology {}", nodeMod, PPrint.topology(topology));
                break;
        }
    }

    private void modifyTerminationPoint(final DataObjectModification<TerminationPoint> nodeChild, final NodeId nodeId) {
        final TerminationPoint terminationPoint = nodeChild.getDataAfter();
        if (terminationPoint != null && terminationPoint.getAugmentation(TerminationPointVbridgeAugment.class) != null) {
            final TerminationPointVbridgeAugment termPointVbridgeAug = terminationPoint.getAugmentation(TerminationPointVbridgeAugment.class);
            final Collection<KeyedInstanceIdentifier<Node, NodeKey>> instanceIdentifiersVPP = nodesToVpps.get(nodeId);
            //TODO: probably iterate via all instance identifiers.
            if (!instanceIdentifiersVPP.isEmpty()) {
                final DataBroker dataBroker = VbdUtil.resolveDataBrokerForMountPoint(instanceIdentifiersVPP.iterator().next(), mountService);
                vppModifier.addInterfaceToBridgeDomainOnVpp(dataBroker, termPointVbridgeAug);
            }
        }
    }

    private L2 createSubInterfaceL2() {
        final RewriteBuilder rewriteBld = new RewriteBuilder();
        rewriteBld.setPopTags((short) 1);

        final BridgeBasedBuilder bridgeBld = new BridgeBasedBuilder();
        bridgeBld.setBridgeDomain(this.bridgeDomainName);
        bridgeBld.setBridgedVirtualInterface(false);

        final L2Builder l2Bld = new L2Builder();
        l2Bld.setRewrite(rewriteBld.build());
        l2Bld.setInterconnection(bridgeBld.build());

        return l2Bld.build();
    }

    private VlanTagged createVlanTagged() {
        final org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev161214.match.attributes.match.type.vlan.tagged.VlanTaggedBuilder
                vlanTagBld1 = new org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev161214.match.attributes.match.type.vlan.tagged.VlanTaggedBuilder();

        final VlanTaggedBuilder vlanTagBuilder = new VlanTaggedBuilder();
        vlanTagBuilder.setVlanTagged(vlanTagBld1.setMatchExactTags(true).build());
        return vlanTagBuilder.build();
    }

    private Match createMatch() {
        final MatchBuilder matchBld = new MatchBuilder();
        matchBld.setMatchType(createVlanTagged());
        return matchBld.build();
    }

    private Tags createTags(VlanId vlan) {
        return new TagsBuilder().setTag(Collections.singletonList(new TagBuilder().setIndex(VLAN_TAG_INDEX_ZERO)
                .setDot1qTag(new Dot1qTagBuilder().setTagType(SVlan.class)
                        .setVlanId(
                                new Dot1qTag.VlanId(
                                        new Dot1qVlanId(vlan.getValue())))
                        .build())
                .build())).build();
    }

    private SubInterface createSubInterface(final VlanId vlan, final Class<? extends VlanType> vlanType) {
        final SubInterfaceBuilder subIntfBld = new SubInterfaceBuilder();
        subIntfBld.setKey(new SubInterfaceKey((long) vlan.getValue()))
            .setIdentifier((long) vlan.getValue())
            .setL2(createSubInterfaceL2())
            .setMatch(createMatch())
            .setEnabled(true)
            .setVlanType(vlanType)
            .setTags(createTags(vlan));
        return subIntfBld.build();
    }

    private void addVlanSubInterface(final NodeId nodeId, final String supIntfKey) {
        // create sub interface from node's defined super interface
        // set subinterface vlan parameters (pop 1)
        // add subinterface to bridge domain
        final VlanNetworkParameters params = (VlanNetworkParameters) config.getTunnelParameters();
        final KeyedInstanceIdentifier<Node, NodeKey> nodeIID = nodesToVpps.get(nodeId).iterator().next();
        final SubInterface subIntf = createSubInterface(params.getVlanId(), params.getVlanType());

        final KeyedInstanceIdentifier<Interface, InterfaceKey> iiToSupIntf =
                InstanceIdentifier.create(Interfaces.class)
                        .child(Interface.class, new InterfaceKey(supIntfKey));

        final KeyedInstanceIdentifier<SubInterface, SubInterfaceKey> iiToVlanSubIntf =
                iiToSupIntf.augmentation(SubinterfaceAugmentation.class)
                        .child(SubInterfaces.class)
                        .child(SubInterface.class, new SubInterfaceKey(subIntf.getKey()));

        final DataBroker vppDataBroker = VbdUtil.resolveDataBrokerForMountPoint(nodeIID, mountService);
        if (vppDataBroker == null) {
            LOG.warn("Cannot get data broker to write interface to node {}", PPrint.node(nodeIID));
            return;
        }

        final WriteTransaction tx = vppDataBroker.newWriteOnlyTransaction();
        tx.put(LogicalDatastoreType.CONFIGURATION, iiToVlanSubIntf, subIntf);

        Futures.addCallback(tx.submit(), new FutureCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void result) {
                LOG.debug("Successfully wrote subinterface {} to node {}", subIntf.getKey().getIdentifier(), nodeId.getValue());
            }

            @Override
            public void onFailure(@Nonnull Throwable t) {
                LOG.warn("Failed to write subinterface {} to node {}", subIntf.getKey().getIdentifier(), nodeId.getValue(), t);
            }
        });
    }

    /**
     * Read IP addresses from interfaces on src and dst nodes to use as vxlan tunnel endpoints.
     * @param iiToSrcVpp source node
     * @param iiToDstVpp destination node
     * @return list of IP addresses configured on interfaces on each node. The returned list should have a size of
     * exactly two, one element for each node. The first element (index 0) is the IP address for the source node endpoint,
     * and the second element (index 1) is the IP address for the destination node endpoint.
     */
    private List<Ipv4AddressNoZone> getTunnelEndpoints(final KeyedInstanceIdentifier<Node, NodeKey> iiToSrcVpp,
                                                       final KeyedInstanceIdentifier<Node, NodeKey> iiToDstVpp) {
        try {
            return vppModifier.readIpAddressesFromVpps(iiToSrcVpp, iiToDstVpp).stream()
            .filter(Objects::nonNull)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
        } catch (final InterruptedException | ExecutionException ex) {
            LOG.warn("Got exception while reading IP addresses from nodes {} and {}", PPrint.node(iiToSrcVpp), PPrint.node(iiToDstVpp), ex);
        }

        return Collections.emptyList();
    }

    /**
     * Get all nodes in the topology which are peers of the target node. A peer node in this instance is a node which
     * should have a link to the given node in a full-mesh topology.
     *
     * @return list of peer nodes
     */
    private List<NodeId> getNodePeers(final NodeId srcNode) {
        return nodesToVpps.keySet().stream()
                .filter(key -> !key.equals(srcNode))
                .collect(Collectors.toList());
    }

    List<KeyedInstanceIdentifier<Node, NodeKey>> getNodePeersByIID(final KeyedInstanceIdentifier<Node, NodeKey> iiToVpp) {
        return nodesToVpps.values().stream()
                .filter(iid -> !iid.equals(iiToVpp))
                .collect(Collectors.toList());
    }

    private void addVxlanTunnel(final NodeId sourceNode) {
        final KeyedInstanceIdentifier<Node, NodeKey> iiToSrcVpp = nodesToVpps.get(sourceNode).iterator().next();

        LOG.debug("adding tunnel to vpp node {} (vbd node is {})", PPrint.node(iiToSrcVpp), sourceNode.getValue());
        for (final NodeId dstNode : getNodePeers(sourceNode)) {
            final KeyedInstanceIdentifier<Node, NodeKey> iiToDstVpp = nodesToVpps.get(dstNode).iterator().next();
            final Integer srcVxlanTunnelId = tunnelIdAllocator.nextIdFor(iiToSrcVpp);
            final Integer dstVxlanTunnelId = tunnelIdAllocator.nextIdFor(iiToDstVpp);
            final List<Ipv4AddressNoZone> endpoints = getTunnelEndpoints(iiToSrcVpp, iiToDstVpp);

            Preconditions.checkState(endpoints.size() == 2, "Got IP address list with wrong size (should be 2, actual size is " + endpoints.size() + ")");

            final Ipv4AddressNoZone ipAddressSrcVpp = endpoints.get(SOURCE_VPP_INDEX);
            final Ipv4AddressNoZone ipAddressDstVpp = endpoints.get(DESTINATION_VPP_INDEX);
            LOG.debug("All required IP addresses for creating tunnel were obtained. (src: {} (node {}), dst: {} (node {}))",
                    ipAddressSrcVpp.getValue(), sourceNode.getValue(), ipAddressDstVpp.getValue(), dstNode.getValue());

            String distinguisher = deriveDistinguisher();

            LOG.debug("Adding term point to dst node {}", dstNode.getValue());
            addTerminationPoint(topology.child(Node.class, new NodeKey(dstNode)), dstVxlanTunnelId);

            LOG.debug("Adding term point to src node {}", sourceNode.getValue());
            addTerminationPoint(topology.child(Node.class, new NodeKey(sourceNode)), srcVxlanTunnelId);

            addLinkBetweenTerminationPoints(sourceNode, dstNode, srcVxlanTunnelId, dstVxlanTunnelId, distinguisher);
            addLinkBetweenTerminationPoints(dstNode, sourceNode, srcVxlanTunnelId, dstVxlanTunnelId, distinguisher);

            //writing v3po:vxlan container to source node
            vppModifier.createVirtualInterfaceOnVpp(ipAddressSrcVpp, ipAddressDstVpp, iiToSrcVpp, srcVxlanTunnelId);

            //writing v3po:vxlan container to existing node
            vppModifier.createVirtualInterfaceOnVpp(ipAddressDstVpp, ipAddressSrcVpp, iiToDstVpp, dstVxlanTunnelId);
        }
    }

    private void removeVxlanInterfaces(final NodeId sourceNode) {
        final KeyedInstanceIdentifier<Node, NodeKey> iiToSrcVpp = nodesToVpps.get(sourceNode).iterator().next();
        for (final NodeId dstNode : getNodePeers(sourceNode)) {
            final KeyedInstanceIdentifier<Node, NodeKey> iiToDstVpp = nodesToVpps.get(dstNode).iterator().next();
            final List<Ipv4AddressNoZone> endpoints = getTunnelEndpoints(iiToSrcVpp, iiToDstVpp);

            Preconditions.checkState(endpoints.size() == 2, "Got IP address list with wrong size (should be 2, actual size is "
                    + endpoints.size() + ")");

            final Ipv4AddressNoZone ipAddressSrcVpp = endpoints.get(SOURCE_VPP_INDEX);
            final Ipv4AddressNoZone ipAddressDstVpp = endpoints.get(DESTINATION_VPP_INDEX);

            // remove bridge domains from vpp
            LOG.debug("Removing bridge domain from vxlan tunnel on node {}", sourceNode);
            vppModifier.deleteVxlanInterface(ipAddressSrcVpp, ipAddressDstVpp, iiToSrcVpp);
            LOG.debug("Removing bridge domain from vxlan tunnel on node {}", dstNode);
            vppModifier.deleteVxlanInterface(ipAddressDstVpp, ipAddressSrcVpp, iiToDstVpp);
        }
    }

    private void addLinkBetweenTerminationPoints(final NodeId newVpp, final NodeId odlVpp,
                                                 final int srcVxlanTunnelId, final int dstVxlanTunnelId,
                                                 final String distinguisher) {
        final String linkIdStr = newVpp.getValue() + "-" + distinguisher + "-" + odlVpp.getValue();
        final LinkId linkId = new LinkId(linkIdStr);
        final KeyedInstanceIdentifier<Link, LinkKey> iiToLink = topology.child(Link.class, new LinkKey(linkId));
        final WriteTransaction wTx = chain.newWriteOnlyTransaction();
        wTx.put(LogicalDatastoreType.OPERATIONAL, iiToLink, prepareLinkData(newVpp, odlVpp, linkId, srcVxlanTunnelId,
                dstVxlanTunnelId), true);
        wTx.submit();
    }

    private static Link prepareLinkData(final NodeId newVpp, final NodeId oldVpp, final LinkId linkId,
                                 final int srcVxlanTunnelId, final int dstVxlanTunnelId) {
        final LinkBuilder linkBuilder = new LinkBuilder();
        linkBuilder.setLinkId(linkId);

        final SourceBuilder sourceBuilder = new SourceBuilder();
        sourceBuilder.setSourceNode(newVpp);
        sourceBuilder.setSourceTp(new TpId(VbdUtil.provideVxlanId(srcVxlanTunnelId)));
        linkBuilder.setSource(sourceBuilder.build());

        final DestinationBuilder destinationBuilder = new DestinationBuilder();
        destinationBuilder.setDestNode(oldVpp);
        destinationBuilder.setDestTp(new TpId(VbdUtil.provideVxlanId(dstVxlanTunnelId)));
        linkBuilder.setDestination(destinationBuilder.build());

        final LinkVbridgeAugmentBuilder linkVbridgeAugmentBuilder = new LinkVbridgeAugmentBuilder();
        linkVbridgeAugmentBuilder.setTunnel(new ExternalReference(VbdUtil.provideVxlanId(srcVxlanTunnelId)));
        linkBuilder.addAugmentation(LinkVbridgeAugment.class, linkVbridgeAugmentBuilder.build());
        return linkBuilder.build();
    }

    private ListenableFuture<Void> createNode(final Node node) {
        List<ListenableFuture<Void>> createdNodesFuture = new ArrayList<>();
        for (SupportingNode supportingNode : node.getSupportingNode()) {
            final NodeId nodeMount = supportingNode.getNodeRef();
            final VbdNetconfConnectionProbe probe = new VbdNetconfConnectionProbe(supportingNode.getNodeRef(), dataBroker);
            try {
                // Verify netconf connection
                boolean connectionReady = probe.startProbing();
                if (connectionReady) {
                    LOG.debug("Node {} is connected, creating ...", supportingNode.getNodeRef());
                    final TopologyId topologyMount = supportingNode.getTopologyRef();
                    final KeyedInstanceIdentifier<Node, NodeKey> iiToVpp = InstanceIdentifier.create(NetworkTopology.class)
                            .child(Topology.class, new TopologyKey(topologyMount))
                            .child(Node.class, new NodeKey(nodeMount));
                    nodesToVpps.put(node.getNodeId(), iiToVpp);
                    ListenableFuture<Void> addVppToBridgeDomainFuture = vppModifier.addVppToBridgeDomain(iiToVpp);
                    createdNodesFuture.add(addSupportingBridgeDomain(addVppToBridgeDomainFuture, node));
                } else {
                    LOG.debug("Failed while connecting to node {}", supportingNode.getNodeRef());
                }
            } catch (InterruptedException | ExecutionException e) {
                LOG.warn("Exception while processing node {} ... ", supportingNode.getNodeRef(), e);
            } catch (TimeoutException e) {
                LOG.warn("Node {} was not connected within {} seconds. Check node configuration and connectivity to proceed",
                        supportingNode.getNodeRef(), VbdNetconfConnectionProbe.NODE_CONNECTION_TIMER);
            }
        }
        // configure all or nothing
        return Futures.transform(Futures.allAsList(createdNodesFuture), new Function<List<Void>, Void>() {

            @Override
            public Void apply(List<Void> input) {
                return null;
            }

        });
    }

    private ListenableFuture<Void> addSupportingBridgeDomain(final ListenableFuture<Void> addVppToBridgeDomainFuture, final Node node) {
        return Futures.transform(addVppToBridgeDomainFuture, new AsyncFunction<Void, Void>() {

            @Override
            public ListenableFuture<Void> apply(@Nonnull Void input) throws Exception {
                LOG.debug("Storing bridge member to operational DS....");
                final BridgeMemberBuilder bridgeMemberBuilder = new BridgeMemberBuilder();
                bridgeMemberBuilder.setSupportingBridgeDomain(new ExternalReference(iiBridgeDomainOnVPPRest));
                final InstanceIdentifier<BridgeMember> iiToBridgeMember = topology.child(Node.class, node.getKey())
                    .augmentation(NodeVbridgeAugment.class)
                    .child(BridgeMember.class);
                final WriteTransaction wTx = chain.newWriteOnlyTransaction();
                wTx.put(LogicalDatastoreType.OPERATIONAL, iiToBridgeMember, bridgeMemberBuilder.build(), true);
                return wTx.submit();
            }
        });
    }

    private void addTerminationPoint(final KeyedInstanceIdentifier<Node, NodeKey> nodeIID, final int vxlanTunnelId) {
        // build data
        final ExternalReference ref = new ExternalReference(VbdUtil.provideVxlanId(vxlanTunnelId));
        final TunnelInterfaceBuilder iFaceBuilder = new TunnelInterfaceBuilder();
        iFaceBuilder.setTunnelInterface(ref);

        final TerminationPointVbridgeAugmentBuilder tpAugmentBuilder = new TerminationPointVbridgeAugmentBuilder();
        tpAugmentBuilder.setInterfaceType(iFaceBuilder.build());

        final TerminationPointBuilder tpBuilder = new TerminationPointBuilder();
        tpBuilder.addAugmentation(TerminationPointVbridgeAugment.class, tpAugmentBuilder.build());
        tpBuilder.setTpId(new TpId(VbdUtil.provideVxlanId(vxlanTunnelId)));
        final TerminationPoint tp = tpBuilder.build();

        // process data
        final WriteTransaction wTx = chain.newWriteOnlyTransaction();
        wTx.put(LogicalDatastoreType.OPERATIONAL, nodeIID.child(TerminationPoint.class, tp.getKey()), tp, true);
        final CheckedFuture<Void, TransactionCommitFailedException> future = wTx.submit();

        Futures.addCallback(future, new FutureCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void result) {
                LOG.debug("Termination point successfully added to {}.", PPrint.node(nodeIID));
            }

            @Override
            public void onFailure(@Nonnull Throwable t) {
                LOG.warn("Failed to add termination point to {}.", PPrint.node(nodeIID));
            }
        });
    }

    private void setConfiguration(final TopologyVbridgeAugment config) {
        LOG.debug("Topology {} configuration set to {}", PPrint.topology(topology), config);

        this.config = config;
    }

    @GuardedBy("this")
    private void updateConfiguration(final DataObjectModification<TopologyVbridgeAugment> mod) {
        LOG.debug("Topology {} configuration changed", PPrint.topology(topology));

        // FIXME: do something smarter
        setConfiguration(mod.getDataAfter());
    }

    private String deriveDistinguisher() {

        String def = "";
        final TunnelParameters tunnelParameters = config.getTunnelParameters();
        final Class<? extends TunnelType> tunnelType = config.getTunnelType();

        if (tunnelType.equals(TunnelTypeVxlan.class)) {
            if (tunnelParameters instanceof VxlanTunnelParameters) {
                final VxlanTunnelParameters vxlanTunnelParams = (VxlanTunnelParameters) tunnelParameters;
                final VxlanVni vni = vxlanTunnelParams.getVni();
                def = vni.getValue().toString();
                int maxLength = (def.length() < MAXLEN) ? def.length() : MAXLEN;
                def = def.substring(0, maxLength);
            }
        }
        return def;
    }
}
