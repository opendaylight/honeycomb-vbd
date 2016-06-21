/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vbd.impl;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.controller.md.sal.binding.api.*;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification.ModificationType;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.vbd.api.VxlanTunnelIdAllocator;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4AddressNoZone;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.VlanId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.external.reference.rev160129.ExternalReference;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev150105.l2.base.attributes.interconnection.BridgeBasedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev150105.VxlanVni;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.network.topology.topology.node.BridgeMember;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.network.topology.topology.node.BridgeMemberBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.network.topology.topology.node.termination.point._interface.type.TunnelInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.network.topology.topology.topology.types.VbridgeTopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.tunnel.vlan.rev160429.NodeVbridgeVlanAugment;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.tunnel.vlan.rev160429.TunnelTypeVlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.tunnel.vlan.rev160429.network.topology.topology.tunnel.parameters.VlanNetworkParameters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.tunnel.vxlan.rev160429.TunnelTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.tunnel.vxlan.rev160429.network.topology.topology.tunnel.parameters.VxlanTunnelParameters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev150527.SubinterfaceAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev150527.VlanType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev150527.interfaces._interface.SubInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev150527.interfaces._interface.sub.interfaces.SubInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev150527.interfaces._interface.sub.interfaces.SubInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev150527.interfaces._interface.sub.interfaces.SubInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev150527.match.attributes.match.type.VlanTagged;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev150527.match.attributes.match.type.VlanTaggedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev150527.sub._interface.base.attributes.L2;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev150527.sub._interface.base.attributes.L2Builder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev150527.sub._interface.base.attributes.Match;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev150527.sub._interface.base.attributes.MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev150527.sub._interface.base.attributes.l2.RewriteBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.*;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.link.attributes.DestinationBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.link.attributes.SourceBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.*;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPointBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.node.attributes.SupportingNode;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Implementation of a single Virtual Bridge Domain. It is bound to a particular network topology instance, manages
 * bridge members and projects state into the operational data store.
 */
final class BridgeDomain implements DataTreeChangeListener<Topology> {
    private static final Logger LOG = LoggerFactory.getLogger(BridgeDomain.class);

    private static final int SOURCE_VPP_INDEX = 0;
    private static final int DESTINATION_VPP_INDEX = 1;
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
    private Multimap<NodeId, KeyedInstanceIdentifier<Node, NodeKey>> nodesToVpps = ArrayListMultimap.create();

    private BridgeDomain(final DataBroker dataBroker, final MountPointService mountService, final KeyedInstanceIdentifier<Topology, TopologyKey> topology,
                         final BindingTransactionChain chain, VxlanTunnelIdAllocator tunnelIdAllocator) {
        this.bridgeDomainName = topology.getKey().getTopologyId().getValue();
        this.vppModifier = new VppModifier(mountService, bridgeDomainName);

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

    static BridgeDomain create(final DataBroker dataBroker,
                               MountPointService mountService, final KeyedInstanceIdentifier<Topology, TopologyKey> topology, final BindingTransactionChain chain,
                               final VxlanTunnelIdAllocator tunnelIdAllocator) {
        return new BridgeDomain(dataBroker, mountService, topology, chain, tunnelIdAllocator);
    }

    synchronized void forceStop() {
        LOG.info("Bridge domain {} for {} going down", this, PPrint.topology(topology));
        reg.close();
        chain.close();
        LOG.info("Bridge domain {} for {} is down", this, PPrint.topology(topology));
    }

    synchronized void stop() {
        LOG.debug("Bridge domain {} for {} shutting down", this, PPrint.topology(topology));

        wipeOperationalState(topology, chain);
        chain.close();
    }

    @Override
    public synchronized void onDataTreeChanged(@Nonnull final Collection<DataTreeModification<Topology>> changes) {
        for (DataTreeModification<Topology> c : changes) {
            LOG.debug("Domain {} for {} processing change {}", this, PPrint.topology(topology), c);

            final DataObjectModification<Topology> mod = c.getRootNode();
            switch (mod.getModificationType()) {
                case DELETE:
                    LOG.debug("Topology {} deleted, expecting shutdown", PPrint.topology(topology));
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
                // FIXME: do something
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
                createNode(newNode);
                if (config.getTunnelType().equals(TunnelTypeVxlan.class)) {
                    final int numberVppsAfterAddition = nodesToVpps.keySet().size();
                    if ((numberVppsBeforeAddition < numberVppsAfterAddition) && (numberVppsBeforeAddition >= 1)) {
                        addTunnel(newNode.getNodeId());
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
        final TerminationPointVbridgeAugment termPointVbridgeAug = terminationPoint.getAugmentation(TerminationPointVbridgeAugment.class);
        if (termPointVbridgeAug != null) {
            final Collection<KeyedInstanceIdentifier<Node, NodeKey>> instanceIdentifiersVPP = nodesToVpps.get(nodeId);
            //TODO: probably iterate via all instance identifiers.
            if (!instanceIdentifiersVPP.isEmpty()) {
                final DataBroker dataBroker = VbdUtil.resolveDataBrokerForMountPoint(instanceIdentifiersVPP.iterator().next(), mountService);
                vppModifier.addInterfaceToBridgeDomainOnVpp(dataBroker, termPointVbridgeAug);
            }
        }
    }

    private L2 createSubInterfaceL2(final Class<? extends VlanType> vlanType) {
        final RewriteBuilder rewriteBld = new RewriteBuilder();
        rewriteBld.setPopTags((short) 1);
        rewriteBld.setVlanType(vlanType);

        final BridgeBasedBuilder bridgeBld = new BridgeBasedBuilder();
        bridgeBld.setBridgeDomain(this.bridgeDomainName);
        bridgeBld.setBridgedVirtualInterface(false);
        bridgeBld.setSplitHorizonGroup((short) 1);

        final L2Builder l2Bld = new L2Builder();
        l2Bld.setRewrite(rewriteBld.build());
        l2Bld.setInterconnection(bridgeBld.build());

        return l2Bld.build();
    }

    private VlanTagged createVlanTagged() {
        final org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev150527.match.attributes.match.type.vlan.tagged.VlanTaggedBuilder
                vlanTagBld1 = new org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev150527.match.attributes.match.type.vlan.tagged.VlanTaggedBuilder();

        final VlanTaggedBuilder vlanTagBuilder = new VlanTaggedBuilder();
        vlanTagBuilder.setVlanTagged(vlanTagBld1.setMatchExactTags(true).build());
        return vlanTagBuilder.build();
    }

    private Match createMatch() {
        final MatchBuilder matchBld = new MatchBuilder();
        matchBld.setMatchType(createVlanTagged());
        return matchBld.build();
    }

    private SubInterface createSubInterface(final VlanId vlan, final Class<? extends VlanType> vlanType) {
        final SubInterfaceBuilder subIntfBld = new SubInterfaceBuilder();
        subIntfBld.setKey(new SubInterfaceKey((long) vlan.getValue()))
                  .setIdentifier((long) vlan.getValue())
                  .setL2(createSubInterfaceL2(vlanType))
                  .setMatch(createMatch())
                  .setEnabled(true)
                  .setVlanType(vlanType);
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
    private List<KeyedInstanceIdentifier<Node, NodeKey>> getNodePeers(final KeyedInstanceIdentifier<Node, NodeKey> nodeIID) {
        return nodesToVpps.entries().stream()
                .filter(entry -> !entry.getValue().getKey().equals(nodeIID.getKey()))
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }

    private void addTunnel(final NodeId sourceNode) {
        final KeyedInstanceIdentifier<Node, NodeKey> iiToSrcVpp = nodesToVpps.get(sourceNode).iterator().next();
        final Integer srcVxlanTunnelId = tunnelIdAllocator.nextIdFor(iiToSrcVpp);

        LOG.debug("adding tunnel to node {}", PPrint.node(iiToSrcVpp));
        for (KeyedInstanceIdentifier<Node, NodeKey> iiToDstVpp : getNodePeers(iiToSrcVpp)) {
            final Integer dstVxlanTunnelId = tunnelIdAllocator.nextIdFor(iiToDstVpp);
            final NodeId dstNode = iiToDstVpp.getKey().getNodeId();
            final List<Ipv4AddressNoZone> endpoints = getTunnelEndpoints(iiToSrcVpp, iiToDstVpp);

            Preconditions.checkState(endpoints.size() == 2, "Got IP address list with wrong size (should be 2, actual size is " + endpoints.size() + ")");

            final Ipv4AddressNoZone ipAddressSrcVpp = endpoints.get(SOURCE_VPP_INDEX);
            final Ipv4AddressNoZone ipAddressDstVpp = endpoints.get(DESTINATION_VPP_INDEX);
            LOG.debug("All required IP addresses for creating tunnel were obtained. (src: {}, dst: {})", ipAddressSrcVpp.getValue(), ipAddressDstVpp.getValue());

            addTerminationPoint(topology.child(Node.class, new NodeKey(dstNode)), dstVxlanTunnelId);
            addTerminationPoint(topology.child(Node.class, new NodeKey(sourceNode)), srcVxlanTunnelId);

            addLinkBetweenTerminationPoints(sourceNode, dstNode, srcVxlanTunnelId, dstVxlanTunnelId);
            addLinkBetweenTerminationPoints(dstNode, sourceNode, srcVxlanTunnelId, dstVxlanTunnelId);

            //writing v3po:vxlan container to source node
            vppModifier.createVirtualInterfaceOnVpp(ipAddressSrcVpp, ipAddressDstVpp, iiToSrcVpp, srcVxlanTunnelId);

            //writing v3po:vxlan container to existing node
            vppModifier.createVirtualInterfaceOnVpp(ipAddressDstVpp, ipAddressSrcVpp, iiToDstVpp, dstVxlanTunnelId);

        }
    }

    private void addLinkBetweenTerminationPoints(final NodeId newVpp, final NodeId odlVpp,
                                                 final int srcVxlanTunnelId, final int dstVxlanTunnelId) {
        //TODO clarify how should identifier of link looks like
        final String linkIdStr = newVpp.getValue() + "-" + odlVpp.getValue();
        final LinkId linkId = new LinkId(linkIdStr);
        final KeyedInstanceIdentifier<Link, LinkKey> iiToLink = topology.child(Link.class, new LinkKey(linkId));
        final WriteTransaction wTx = chain.newWriteOnlyTransaction();
        wTx.put(LogicalDatastoreType.OPERATIONAL, iiToLink, prepareLinkData(newVpp, odlVpp, linkId, srcVxlanTunnelId, dstVxlanTunnelId), true);
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

    private void createNode(final Node node) {
        for (SupportingNode supportingNode : node.getSupportingNode()) {
            final NodeId nodeMount = supportingNode.getNodeRef();
            final TopologyId topologyMount = supportingNode.getTopologyRef();

            final KeyedInstanceIdentifier<Node, NodeKey> iiToVpp = InstanceIdentifier
                    .create(NetworkTopology.class)
                    .child(Topology.class, new TopologyKey(topologyMount))
                    .child(Node.class, new NodeKey(nodeMount));
            nodesToVpps.put(node.getNodeId(), iiToVpp);
            ListenableFuture<Void> addVppToBridgeDomainFuture = vppModifier.addVppToBridgeDomain(iiToVpp);
            addSupportingBridgeDomain(addVppToBridgeDomainFuture, node);
        }
    }

    private void addSupportingBridgeDomain(final ListenableFuture<Void> addVppToBridgeDomainFuture, final Node node) {
        Futures.addCallback(addVppToBridgeDomainFuture, new FutureCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                LOG.debug("Storing bridge member to operational DS....");
                final BridgeMemberBuilder bridgeMemberBuilder = new BridgeMemberBuilder();
                bridgeMemberBuilder.setSupportingBridgeDomain(new ExternalReference(iiBridgeDomainOnVPPRest));
                final InstanceIdentifier<BridgeMember> iiToBridgeMember = topology.child(Node.class, node.getKey()).augmentation(NodeVbridgeAugment.class).child(BridgeMember.class);
                final WriteTransaction wTx = chain.newWriteOnlyTransaction();
                wTx.put(LogicalDatastoreType.OPERATIONAL, iiToBridgeMember, bridgeMemberBuilder.build(), true);
                wTx.submit();
            }

            @Override
            public void onFailure(Throwable t) {
                //TODO handle this state
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
                LOG.debug("Termination point successfully added to {}.", nodeIID);
            }

            @Override
            public void onFailure(@Nonnull Throwable t) {
                LOG.warn("Failed to add termination point to {}.", nodeIID);
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
}
