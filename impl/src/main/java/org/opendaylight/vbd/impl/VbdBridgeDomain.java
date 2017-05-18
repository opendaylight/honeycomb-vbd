/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vbd.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

import org.opendaylight.controller.md.sal.binding.api.BindingTransactionChain;
import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.binding.api.MountPointService;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.vbd.api.VxlanTunnelIdAllocator;
import org.opendaylight.vbd.impl.transaction.VbdNetconfConnectionProbe;
import org.opendaylight.vbd.impl.transaction.VbdNetconfTransaction;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4AddressNoZone;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.external.reference.rev160129.ExternalReference;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev170315.VxlanVni;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.NodeVbridgeAugment;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.TerminationPointVbridgeAugment;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.TerminationPointVbridgeAugmentBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.TopologyVbridgeAugment;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.network.topology.topology.node.BridgeMember;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.network.topology.topology.node.BridgeMemberBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.network.topology.topology.node.termination.point._interface.type.TunnelInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.tunnel.vlan.rev170327.NodeVbridgeVlanAugment;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.tunnel.vlan.rev170327.TunnelTypeVlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.tunnel.vlan.rev170327.network.topology.topology.tunnel.parameters.VlanNetworkParameters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.tunnel.vxlan.rev170327.TunnelTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.tunnel.vxlan.rev170327.network.topology.topology.tunnel.parameters.VxlanTunnelParameters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev170509.SubinterfaceAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev170509.interfaces._interface.SubInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev170509.interfaces._interface.sub.interfaces.SubInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev170509.interfaces._interface.sub.interfaces.SubInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.LinkId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TpId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.link.attributes.Destination;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.link.attributes.Source;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.LinkKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPointBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.node.attributes.SupportingNode;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

/**
 * Implementation of a single Virtual Bridge Domain. It is bound to a particular network topology instance, manages
 * bridge members and projects state into the operational data store.
 */
public final class VbdBridgeDomain implements ClusteredDataTreeChangeListener<Topology> {
    private static final Logger LOG = LoggerFactory.getLogger(VbdBridgeDomain.class);

    static final short VLAN_TAG_INDEX_ZERO = 0;
    static final int MAXLEN = 8;
    private static final int SOURCE_VPP_INDEX = 0;
    private static final int DESTINATION_VPP_INDEX = 1;
    private final DataBroker dataBroker;
    private final KeyedInstanceIdentifier<Topology, TopologyKey> topology;
    @GuardedBy("this")
    private final BindingTransactionChain chain;
    private final ListenerRegistration<?> reg;
    private final MountPointService mountService;
    private final VppModifier vppModifier;
    private final VxlanTunnelIdAllocator tunnelIdAllocator;
    private final String bridgeDomainName;
    private final String iiBridgeDomainOnVPPRest;
    private final VxlanVni vniValue;
    private TopologyVbridgeAugment config;
    @SuppressWarnings("FieldCanBeLocal")
    private Multimap<NodeId, KeyedInstanceIdentifier<Node, NodeKey>> nodesToVpps = ArrayListMultimap.create();

    private VbdBridgeDomain(final DataBroker dataBroker, final MountPointService mountService, final KeyedInstanceIdentifier<Topology, TopologyKey> topology,
                            final BindingTransactionChain chain, VxlanTunnelIdAllocator tunnelIdAllocator) throws Exception {
        this.chain = Preconditions.checkNotNull(chain);
        this.dataBroker = Preconditions.checkNotNull(dataBroker);
        this.topology = Preconditions.checkNotNull(topology);
        this.bridgeDomainName = topology.getKey().getTopologyId().getValue();
        this.vniValue = leverageVxlanVni(topology);
        this.vppModifier = new VppModifier(mountService, bridgeDomainName, this);
        this.mountService = mountService;
        this.tunnelIdAllocator = tunnelIdAllocator;
        this.iiBridgeDomainOnVPPRest = VbdUtil.provideIidBridgeDomainOnVPPRest(bridgeDomainName);

        wipeOperationalState(topology);
        createFreshOperationalState(topology);
        readParams(topology);

        reg = dataBroker.registerDataTreeChangeListener(
                new DataTreeIdentifier<>(LogicalDatastoreType.CONFIGURATION, topology), this);
    }

    /**
     * Topology listener registered for every created bridge domain.
     * @param changes topology modification events
     */
    @Override
    public synchronized void onDataTreeChanged(@Nonnull final Collection<DataTreeModification<Topology>> changes) {
        for (DataTreeModification<Topology> change : changes) {
            LOG.debug("Bridge domain {} for {} processing change {}", this.bridgeDomainName, PPrint.topology(topology),
                    change.getClass());
            final DataObjectModification<Topology> modification = change.getRootNode();
            ListenableFuture<Void> modificationTask;
            switch (modification.getModificationType()) {
                case WRITE:
                    modificationTask = handleNewTopology(modification);
                    break;
                case SUBTREE_MODIFIED:
                    modificationTask = handleModifiedTopology(modification);
                    break;
                case DELETE:
                    LOG.debug("Topology {} deleted, expecting shutdown", PPrint.topology(topology));
                    modificationTask = deleteBridgeDomain();
                    break;
                default:
                    LOG.warn("Unhandled topology modification {}", modification);
                    modificationTask = Futures.immediateFuture(null);
                    break;
            }
            Futures.addCallback(modificationTask, new FutureCallback<Void>() {
                @Override
                public void onSuccess(@Nullable Void aVoid) {
                    LOG.info("Topology change {} for bridge domain {} completed", modification.getModificationType(),
                            PPrint.topology(topology));
                }

                @Override
                public void onFailure(@Nonnull Throwable throwable) {
                    LOG.warn("Topology change {} for bridge domain {} failed: {}", modification.getModificationType(),
                            PPrint.topology(topology), throwable);
                }
            });
        }
    }

    private ListenableFuture<Void> handleNewTopology(final DataObjectModification<Topology> modification) {
        Preconditions.checkNotNull(modification.getDataAfter());
        final Topology data = modification.getDataAfter();
        // Handle VBridge augmentation
        final TopologyVbridgeAugment vbdConfiguration = data.getAugmentation(TopologyVbridgeAugment.class);
        if (vbdConfiguration != null) {
            // Spread configuration
            setConfiguration(vbdConfiguration);
            vppModifier.setConfig(vbdConfiguration);
        }
        else {
            LOG.error("Topology {} has no configuration", PPrint.topology(topology));
        }
        // Handle new nodes
        final Collection<DataObjectModification<? extends DataObject>> modifiedChildren = modification.getModifiedChildren();
        final List<ListenableFuture<Void>> newCumulativeTopologyResult = new ArrayList<>();
        for (final DataObjectModification<? extends DataObject> childNode : modifiedChildren) {
            LOG.debug("Processing created child {} from topology {}", childNode, PPrint.topology(topology));
            if (Node.class.isAssignableFrom(childNode.getDataType())) {
                newCumulativeTopologyResult.add(handleModifiedNode(childNode));
            }
        }
        final ListenableFuture<List<Void>> newTopologyResult = Futures.allAsList(newCumulativeTopologyResult);
        return transform(newTopologyResult);
    }

    private ListenableFuture<Void> handleModifiedTopology(final DataObjectModification<Topology> modification) {
        Preconditions.checkNotNull(modification.getDataAfter());
        final DataObjectModification<TopologyVbridgeAugment> topologyModification =
                modification.getModifiedAugmentation(TopologyVbridgeAugment.class);
        // Handle VBridge augmentation
        if (topologyModification != null &&
                !DataObjectModification.ModificationType.DELETE.equals(topologyModification.getModificationType())) {
            // Update configuration
            updateConfiguration(topologyModification);
        }
        // Handle new/modified nodes
        final Collection<DataObjectModification<? extends DataObject>> modifiedChildren = modification.getModifiedChildren();
        final List<ListenableFuture<Void>> updatedCumulativeTopologyTask = new ArrayList<>();
        for (final DataObjectModification<? extends DataObject> childNode : modifiedChildren) {
            LOG.debug("Processing modified child {} from topology {}", childNode, PPrint.topology(topology));
            if (Node.class.isAssignableFrom(childNode.getDataType())) {
                updatedCumulativeTopologyTask.add(handleModifiedNode(childNode));
            }
        }
        final ListenableFuture<List<Void>> updatedTopologyResult = Futures.allAsList(updatedCumulativeTopologyTask);
        return transform(updatedTopologyResult);
    }

    private ListenableFuture<Void> deleteBridgeDomain() {
        LOG.debug("Deleting entire bridge domain {}", bridgeDomainName);
        final Collection<KeyedInstanceIdentifier<Node, NodeKey>> vppNodes = nodesToVpps.values();
        final List<ListenableFuture<Void>> deleteBdTask = new ArrayList<>();
        vppNodes.forEach((vppNode) -> deleteBdTask.add(vppModifier.deleteBridgeDomainFromVppNode(vppNode)));
        nodesToVpps.clear();
        final ListenableFuture<List<Void>> cumulativeDeleteBdTask = Futures.allAsList(deleteBdTask);
        return transform(cumulativeDeleteBdTask);
    }

    /**
     * In case new topology is created, or existing topology is updated, every new/modified node is resolved.
     *
     * @param nodeModification current node modification
     * @return {@link ListenableFuture} of actual task
     */
    private ListenableFuture<Void> handleModifiedNode(final DataObjectModification<? extends DataObject> nodeModification) {
        switch (nodeModification.getModificationType()) {
            case WRITE:
                LOG.debug("Topology {} node {} created", PPrint.topology(topology), nodeModification.getIdentifier());
                final Node newNode = (Node) nodeModification.getDataAfter();
                if (newNode == null) {
                    LOG.warn("Provided node is null");
                    return Futures.immediateFuture(null);
                }
                return handleNewModifiedNode(newNode);
            case SUBTREE_MODIFIED:
                LOG.debug("Topology {} node {} modified", PPrint.topology(topology), nodeModification.getIdentifier());
                final HashMap<TerminationPoint, Node> modifiedNodes = new HashMap<>();
                for (DataObjectModification<? extends DataObject> nodeChild : nodeModification.getModifiedChildren()) {
                    if (TerminationPoint.class.isAssignableFrom(nodeChild.getDataType())) {
                        final TerminationPoint modifedTerminationPoint = (TerminationPoint) nodeChild.getDataAfter();
                        final Node modifiedNode = (Node) nodeChild.getDataAfter();
                        modifiedNodes.put(modifedTerminationPoint, modifiedNode);
                    }
                }
                return handleUpdatedModifiedNodes(modifiedNodes);
            case DELETE:
                LOG.debug("Topology {} node {} deleted", PPrint.topology(topology), nodeModification.getIdentifier());
                final Node deletedNode = (Node) nodeModification.getDataBefore();
                return handleDeletedModifiedNode(deletedNode);
            default:
                LOG.warn("Unhandled node modification {} in topology {}", nodeModification, PPrint.topology(topology));
                break;
        }
        return Futures.immediateFuture(null);
    }

    private ListenableFuture<Void> handleNewModifiedNode(@Nonnull final Node newNode) {
        LOG.debug("Topology {} node {} created", PPrint.topology(topology), newNode.getNodeId().getValue());
        final int numberOfVppsBeforeAddition = nodesToVpps.keySet().size();
        final ListenableFuture<Void> createNodeTask = createNode(newNode);
        return Futures.transform(createNodeTask, new Function<Void, Void>() {
            @Nullable
            @Override
            public Void apply(@Nullable Void input) {
                try {
                    // Vxlan tunnel type
                    if (config.getTunnelType().equals(TunnelTypeVxlan.class)) {
                        final int numberOfVppsAfterAddition = nodesToVpps.keySet().size();
                        if ((numberOfVppsBeforeAddition <= numberOfVppsAfterAddition) && (numberOfVppsBeforeAddition >= 1)) {
                            return addVxlanTunnel(newNode.getNodeId()).get();
                        }
                    // Vlan tunnel type
                    } else if (config.getTunnelType().equals(TunnelTypeVlan.class)) {
                        final NodeVbridgeVlanAugment vlanAug = newNode.getAugmentation(NodeVbridgeVlanAugment.class);
                        return addVlanSubInterface(newNode.getNodeId(), vlanAug.getSuperInterface()).get();
                    } else {
                        LOG.warn("Unknown tunnel type {}", config.getTunnelType());
                    }
                }
                catch (InterruptedException | ExecutionException e) {
                    LOG.warn("Exception while processing tunnel for new node {}", newNode.getNodeId().getValue());
                }
                return null;
            }
        });
    }

    private ListenableFuture<Void> handleUpdatedModifiedNodes(final HashMap<TerminationPoint, Node> modifiedNodes) {
        final List<ListenableFuture<Void>> cumulativeTask = new ArrayList<>();
        modifiedNodes.forEach( (terminationPoint, node) -> {
            if (terminationPoint != null && terminationPoint.getAugmentation(TerminationPointVbridgeAugment.class) != null
                    && node.getNodeId() != null) {
                final TerminationPointVbridgeAugment termPointVbridgeAug = terminationPoint.getAugmentation(TerminationPointVbridgeAugment.class);
                final Collection<KeyedInstanceIdentifier<Node, NodeKey>> instanceIdentifiersVPP = nodesToVpps.get(node.getNodeId());
                //TODO: probably iterate via all instance identifiers.
                if (!instanceIdentifiersVPP.isEmpty()) {
                    final DataBroker dataBroker = VbdUtil.resolveDataBrokerForMountPoint(instanceIdentifiersVPP.iterator().next(), mountService);
                    cumulativeTask.add(vppModifier.addInterfaceToBridgeDomainOnVpp(dataBroker, termPointVbridgeAug));
                }
            }
        } );
        final ListenableFuture<List<Void>> completedCumulativeTask = Futures.allAsList(cumulativeTask);
        return transform(completedCumulativeTask);
    }

    private ListenableFuture<Void> handleDeletedModifiedNode(@Nullable final Node deletedNode) {
        if (deletedNode == null) {
            LOG.warn("Unable to remove bridge domain, provided node is null. Bridge domain {}", bridgeDomainName);
            return Futures.immediateFuture(null);
        }
        final KeyedInstanceIdentifier<Node, NodeKey> vppNodeIid = nodesToVpps.get(deletedNode.getNodeId()).iterator().next();
        final KeyedInstanceIdentifier<Node, NodeKey> backingNodeIid = topology.child(Node.class, deletedNode.getKey());
        LOG.debug("Removing node from BD. Node: {}. backing node: {}", PPrint.node(vppNodeIid),
                PPrint.node(backingNodeIid));
        final ListenableFuture<Void> removeNodeTask = removeNodeFromBridgeDomain(vppNodeIid, backingNodeIid);
        return Futures.transform(removeNodeTask, new Function<Void, Void>() {
            @Nullable
            @Override
            public Void apply(@Nullable Void input) {
                ListenableFuture<Void> future = Futures.immediateFuture(null);
                String message = null;
                // Vxlan case
                if (config.getTunnelType().equals(TunnelTypeVxlan.class)) {
                    future = removeVxlanInterfaces(deletedNode.getNodeId());
                    message = String.format("Remove vxlan interfaces processing failed. Node: %s", deletedNode.getNodeId());
                // Vlan case
                } else if (config.getTunnelType().equals(TunnelTypeVlan.class)) {
                    // TODO sub-interfaces cannot be removed
                } else {
                    LOG.warn("Unknown interface type: {}", config.getTunnelType());
                }
                try {
                    return future.get();
                } catch (InterruptedException | ExecutionException e) {
                    LOG.warn(message);
                    return null;
                }
            }
        });
    }

    private ListenableFuture<Void> wipeOperationalState(final KeyedInstanceIdentifier<Topology, TopologyKey> topology) {
        LOG.info("Wiping operational state of {}", PPrint.topology(topology));
        final WriteTransaction tx = chain.newWriteOnlyTransaction();
        tx.delete(LogicalDatastoreType.OPERATIONAL, topology);
        return tx.submit();
    }

    private void createFreshOperationalState(final KeyedInstanceIdentifier<Topology, TopologyKey> topology) {
        LOG.info("Creating fresh operational state for {}", PPrint.topology(topology));

        final WriteTransaction tx = chain.newWriteOnlyTransaction();
        tx.put(LogicalDatastoreType.OPERATIONAL, topology, VbdUtil.buildFreshTopology(topology), true);
        tx.submit();
    }

    private void readParams(final KeyedInstanceIdentifier<Topology, TopologyKey> topology) {
        final ReadOnlyTransaction tx = chain.newReadOnlyTransaction();
        Futures.addCallback(tx.read(LogicalDatastoreType.CONFIGURATION, topology), new FutureCallback<Optional<Topology>>() {
            @Override
            public void onSuccess(@Nullable Optional<Topology> result) {
                if (result != null) {
                    if (result.isPresent()) {
                        VbdUtil.printVbridgeParams(result.get());
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
                                  @Nonnull final BindingTransactionChain chain,
                                  final VxlanTunnelIdAllocator tunnelIdAllocator) throws Exception {
        return new VbdBridgeDomain(dataBroker, mountService, topology, chain, tunnelIdAllocator);
    }

    synchronized void forceStop() {
        // TODO better be to return future
        LOG.debug("Bridge domain {} for {} going down", this, PPrint.topology(topology));
        reg.close();
        LOG.info("Bridge domain {} for {} is down", this, PPrint.topology(topology));
    }

    synchronized void stop() {
        LOG.debug("Bridge domain {} for {} shutting down", this, PPrint.topology(topology));
        wipeOperationalState(topology);
        chain.close();
    }

    private ListenableFuture<List<InstanceIdentifier<Link>>> findPrunableLinks(final KeyedInstanceIdentifier<Node, NodeKey> vbdNode) {
        LOG.debug("Finding prunable links for node {}", PPrint.node(vbdNode));

        final NodeId deletedNodeId = vbdNode.getKey().getNodeId();

        // read the topology to find the links
        final ReadOnlyTransaction rTx = dataBroker.newReadOnlyTransaction();
        return Futures.transform(rTx.read(LogicalDatastoreType.OPERATIONAL, topology), (AsyncFunction<Optional<Topology>,
                List<InstanceIdentifier<Link>>>) result -> {
            final List<InstanceIdentifier<Link>> prunableLinks = new ArrayList<>();

            if (result.isPresent()) {
                final List<Link> links = result.get().getLink();

                for (final Link link : nullToEmpty(links)) {
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
                    final WriteTransaction wTx = dataBroker.newWriteOnlyTransaction();
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

    private ListenableFuture<Void> removeNodeFromBridgeDomain(final KeyedInstanceIdentifier<Node, NodeKey> vppNode,
                                                              final KeyedInstanceIdentifier<Node, NodeKey> backingNode) {
        LOG.debug("Removing node {} from bridge domain {}", PPrint.node(vppNode), bridgeDomainName);
        final ListenableFuture<Void> deleteNodeTask = vppModifier.deleteBridgeDomainFromVppNode(vppNode);
        pruneLinks(backingNode);

        // remove this node from vbd operational topology
        final WriteTransaction wTx3 = dataBroker.newWriteOnlyTransaction();
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
        return deleteNodeTask;
    }

    private ListenableFuture<Void> addVlanSubInterface(final NodeId nodeId, final String supIntfKey) {
        // create sub interface from node's defined super interface
        // set subinterface vlan parameters (pop 1)
        // add subinterface to bridge domain
        final VlanNetworkParameters params = (VlanNetworkParameters) config.getTunnelParameters();
        final KeyedInstanceIdentifier<Node, NodeKey> nodeIID = nodesToVpps.get(nodeId).iterator().next();
        final SubInterface subIntf = VbdUtil.createSubInterface(params.getVlanId(), params.getVlanType(), bridgeDomainName);

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
            return Futures.immediateFuture(null);
        }
        final boolean transactionState = VbdNetconfTransaction.netconfSyncedWrite(vppDataBroker, iiToVlanSubIntf, subIntf,
                VbdNetconfTransaction.RETRY_COUNT);
        if (transactionState) {
            LOG.debug("Successfully wrote subinterface {} to node {}", subIntf.getKey().getIdentifier(), nodeId.getValue());
        } else {
            LOG.warn("Failed to write subinterface {} to node {}", subIntf.getKey().getIdentifier(), nodeId.getValue());
        }
        return Futures.immediateFuture(null);
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
            return vppModifier.readIpAddressesFromVpps(dataBroker, iiToSrcVpp, iiToDstVpp).stream()
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

    private ListenableFuture<Void> addVxlanTunnel(final NodeId sourceNode) {
        final KeyedInstanceIdentifier<Node, NodeKey> iiToSrcVpp = nodesToVpps.get(sourceNode).iterator().next();
        List<ListenableFuture<Void>> cumulativeTask = new ArrayList<>();

        LOG.debug("adding tunnel to vpp node {} (vbd node is {})", PPrint.node(iiToSrcVpp), sourceNode.getValue());
        for (final NodeId dstNode : getNodePeers(sourceNode)) {
            List<ListenableFuture<Void>> perPeerTask = new ArrayList<>();
            final KeyedInstanceIdentifier<Node, NodeKey> iiToDstVpp = nodesToVpps.get(dstNode).iterator().next();
            final Integer srcVxlanTunnelId = tunnelIdAllocator.nextIdFor(iiToSrcVpp);
            final Integer dstVxlanTunnelId = tunnelIdAllocator.nextIdFor(iiToDstVpp);
            final List<Ipv4AddressNoZone> endpoints = getTunnelEndpoints(iiToSrcVpp, iiToDstVpp);

            Preconditions.checkState(endpoints.size() == 2,
                    "Got IP address list with wrong size (should be 2, actual size is " + endpoints.size() + ")");

            final Ipv4AddressNoZone ipAddressSrcVpp = endpoints.get(SOURCE_VPP_INDEX);
            final Ipv4AddressNoZone ipAddressDstVpp = endpoints.get(DESTINATION_VPP_INDEX);
            LOG.debug("All required IP addresses for creating tunnel were obtained. (src: {} (node {}), dst: {} (node {}))",
                    ipAddressSrcVpp.getValue(), sourceNode.getValue(), ipAddressDstVpp.getValue(), dstNode.getValue());

            String distinguisher = VbdUtil.deriveDistinguisher(config);
            // Termination Points
            LOG.debug("Adding term point to dst node {}", dstNode.getValue());
            perPeerTask.add(addTerminationPoint(topology.child(Node.class, new NodeKey(dstNode)), dstVxlanTunnelId));
            LOG.debug("Adding term point to src node {}", sourceNode.getValue());
            perPeerTask.add(addTerminationPoint(topology.child(Node.class, new NodeKey(sourceNode)), srcVxlanTunnelId));

            // Links between termination points
            perPeerTask.add(addLinkBetweenTerminationPoints(sourceNode, dstNode, srcVxlanTunnelId, dstVxlanTunnelId, distinguisher));
            perPeerTask.add(addLinkBetweenTerminationPoints(dstNode, sourceNode, srcVxlanTunnelId, dstVxlanTunnelId, distinguisher));

            // Virtual interfaces
            perPeerTask.add(vppModifier.createVirtualInterfaceOnVpp(ipAddressSrcVpp, ipAddressDstVpp, vniValue,
                    iiToSrcVpp, srcVxlanTunnelId));
            perPeerTask.add(vppModifier.createVirtualInterfaceOnVpp(ipAddressDstVpp, ipAddressSrcVpp, vniValue,
                    iiToDstVpp, dstVxlanTunnelId));

            final ListenableFuture<List<Void>> processedPerPeerTask = Futures.allAsList(perPeerTask);
            cumulativeTask.add(transform(processedPerPeerTask));
        }
        final ListenableFuture<List<Void>> processedCumulativeTask = Futures.allAsList(cumulativeTask);
        return transform(processedCumulativeTask);
    }

    private ListenableFuture<Void> removeVxlanInterfaces(final NodeId sourceNode) {
        final KeyedInstanceIdentifier<Node, NodeKey> iiToSrcVpp = nodesToVpps.get(sourceNode).iterator().next();
        final List<ListenableFuture<Void>> deleteVxlanTaskList = new ArrayList<>();
        for (final NodeId dstNode : getNodePeers(sourceNode)) {
            final KeyedInstanceIdentifier<Node, NodeKey> iiToDstVpp = nodesToVpps.get(dstNode).iterator().next();
            final List<Ipv4AddressNoZone> endpoints = getTunnelEndpoints(iiToSrcVpp, iiToDstVpp);

            Preconditions.checkState(endpoints.size() == 2,
                    "Got IP address list with wrong size (should be 2, actual size is {})", endpoints.size());

            final Ipv4AddressNoZone ipAddressSrcVpp = endpoints.get(SOURCE_VPP_INDEX);
            final Ipv4AddressNoZone ipAddressDstVpp = endpoints.get(DESTINATION_VPP_INDEX);

            // remove bridge domains from vpp
            LOG.debug("Removing bridge domain from vxlan tunnel on node {}", sourceNode);
            deleteVxlanTaskList.add(vppModifier.deleteVxlanInterface(ipAddressSrcVpp, ipAddressDstVpp, vniValue,
                    iiToSrcVpp));
            LOG.debug("Removing bridge domain from vxlan tunnel on node {}", dstNode);
            deleteVxlanTaskList.add(vppModifier.deleteVxlanInterface(ipAddressDstVpp, ipAddressSrcVpp, vniValue,
                    iiToDstVpp));
        }
        final ListenableFuture<List<Void>> cumulativeDeleteVxlanTask = Futures.allAsList(deleteVxlanTaskList);
        return transform(cumulativeDeleteVxlanTask);
    }

    private ListenableFuture<Void> addLinkBetweenTerminationPoints(final NodeId newVpp, final NodeId odlVpp,
                                                                   final int srcVxlanTunnelId, final int dstVxlanTunnelId,
                                                                   final String distinguisher) {
        final String linkIdStr = newVpp.getValue() + "-" + distinguisher + "-" + odlVpp.getValue();
        final LinkId linkId = new LinkId(linkIdStr);
        final KeyedInstanceIdentifier<Link, LinkKey> iiToLink = topology.child(Link.class, new LinkKey(linkId));
        final WriteTransaction wTx = chain.newWriteOnlyTransaction();
        wTx.put(LogicalDatastoreType.OPERATIONAL, iiToLink, VbdUtil.prepareLinkData(newVpp, odlVpp, linkId, srcVxlanTunnelId,
                dstVxlanTunnelId), true);
        LOG.debug("Adding link between termination points {} and {}", srcVxlanTunnelId, dstVxlanTunnelId);
        return wTx.submit();
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
        return Futures.transform(Futures.allAsList(createdNodesFuture), (Function<List<Void>, Void>) input -> null);
    }

    private ListenableFuture<Void> addSupportingBridgeDomain(final ListenableFuture<Void> addVppToBridgeDomainFuture,
                                                             final Node node) {
        return Futures.transform(addVppToBridgeDomainFuture, (AsyncFunction<Void, Void>) input -> {
            LOG.debug("Storing bridge member to operational DS....");
            final BridgeMemberBuilder bridgeMemberBuilder = new BridgeMemberBuilder();
            bridgeMemberBuilder.setSupportingBridgeDomain(new ExternalReference(iiBridgeDomainOnVPPRest));
            final InstanceIdentifier<BridgeMember> iiToBridgeMember = topology.child(Node.class, node.getKey())
                    .augmentation(NodeVbridgeAugment.class)
                    .child(BridgeMember.class);
            final WriteTransaction wTx = chain.newWriteOnlyTransaction();
            wTx.put(LogicalDatastoreType.OPERATIONAL, iiToBridgeMember, bridgeMemberBuilder.build(), true);
            return wTx.submit();
        });
    }

    private ListenableFuture<Void> addTerminationPoint(final KeyedInstanceIdentifier<Node, NodeKey> nodeIID,
                                                       final int vxlanTunnelId) {
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
        LOG.debug("Adding termination point to node {}", nodeIID.getKey());
        return wTx.submit();
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

    /**
     * Read vxlan VNI from bridge domain/topology. This value is stored for further processing. VNI is null for
     * bridge domains without {@link TopologyVbridgeAugment} augmentation or if augmentation is not an instance of
     * {@link VxlanTunnelParameters}
     */
    @Nullable
    private VxlanVni leverageVxlanVni(KeyedInstanceIdentifier<Topology, TopologyKey> topology) {
        final ReadOnlyTransaction rTx = dataBroker.newReadOnlyTransaction();
        final CheckedFuture<Optional<Topology>, ReadFailedException> futureTopology =
                rTx.read(LogicalDatastoreType.CONFIGURATION, topology);
        try {
            final Optional<Topology> optionalBdTopology = futureTopology.get();
            if (optionalBdTopology.isPresent()) {
                final Topology bdTopology = optionalBdTopology.get();
                final TopologyVbridgeAugment augmentation = bdTopology.getAugmentation(TopologyVbridgeAugment.class);
                if (augmentation != null && augmentation.getTunnelParameters() != null
                        && augmentation.getTunnelParameters() instanceof VxlanTunnelParameters) {
                    final VxlanTunnelParameters params = (VxlanTunnelParameters) augmentation.getTunnelParameters();
                    if (params.getVni() != null) {
                        return params.getVni();
                    }
                    LOG.warn("Vni is null for bridge domain {}", bridgeDomainName);
                }
                LOG.debug("Topology bridge domain augmentation not found for {}", topology.getKey());
            }
            LOG.warn("Topology {} not found", topology.getKey());
            return null;
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Topology cannot be read, cause {}", e);
            return null;
        }
    }

    private <T> List<T> nullToEmpty(final List<T> list) {
        if (list == null) {
            return Collections.emptyList();
        }
        return list;
    }

    // Transform future util method
    private ListenableFuture<Void> transform(final ListenableFuture<List<Void>> input) {
        return Futures.transform(input, new Function<List<Void>, Void>() {
            @Nullable
            @Override
            public Void apply(@Nullable List<Void> input) {
                // NOOP
                return null;
            }
        });
    }
}
