/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vbd.impl;

import static org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeConnectionStatus.ConnectionStatus.Connected;
import static org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeConnectionStatus.ConnectionStatus.Connecting;

import javax.annotation.Nonnull;
import java.util.Collection;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.SettableFuture;
import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeConnectionStatus.ConnectionStatus;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Purpose: verify whether provided netconf node is already connected or wait if not.
 *
 * VbdNetconfConnectionProbe registers listener which catches node-related changes from topology-netconf. Provided
 * {@link SettableFuture<Boolean>} future is set according to result: {@link Boolean#TRUE} if node is
 * connected and {@link Boolean#FALSE} if node is not able to connect or if node is not a netconf device.
 *
 * While {@link ConnectionStatus#Connecting}, listener waits for another update. Timer is set in
 * {@link VbdBridgeDomain} to prevent stuck
 *
 */
class VbdNetconfConnectionProbe implements ClusteredDataTreeChangeListener<Node> {

    private static final Logger LOG = LoggerFactory.getLogger(VbdNetconfConnectionProbe.class);

    private final ListenerRegistration<VbdNetconfConnectionProbe> registeredListener;
    private final DataTreeIdentifier<Node> path;
    private SettableFuture<Boolean> futureStatus;
    @SuppressWarnings("FieldCanBeLocal")
    private final String TOPOLOGY_ID = "topology-netconf";

    VbdNetconfConnectionProbe(final NodeId nodeId, final SettableFuture<Boolean> futureStatus, final DataBroker dataBroker) {
        this.futureStatus = Preconditions.checkNotNull(futureStatus);
        Preconditions.checkArgument(!futureStatus.isDone());
        Preconditions.checkNotNull(nodeId);
        Preconditions.checkNotNull(dataBroker);
        final InstanceIdentifier<Node> nodeIid = InstanceIdentifier.builder(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(new TopologyId(TOPOLOGY_ID)))
                .child(Node.class, new NodeKey(nodeId))
                .build();
        path = new DataTreeIdentifier<>(LogicalDatastoreType.OPERATIONAL, nodeIid);
        LOG.debug("Registering listener for node {}", nodeId);
        registeredListener = dataBroker.registerDataTreeChangeListener(path, this);
    }

    @Override
    public void onDataTreeChanged(@Nonnull Collection<DataTreeModification<Node>> changes) {
        changes.forEach(modification -> {
            final DataObjectModification<Node> rootNode = modification.getRootNode();
            final Node node = rootNode.getDataAfter();
            final NetconfNode netconfNode = getNodeAugmentation(node);
            if (node == null || node.getNodeId() == null) {
                return;
            }
            final NodeId nodeId = node.getNodeId();
            if (netconfNode == null || netconfNode.getConnectionStatus() == null) {
                LOG.warn("Node {} does not contain netconf augmentation");
                futureStatus.set(false);
                unregister();
            } else {
                final ConnectionStatus status = netconfNode.getConnectionStatus();
                if (status.equals(Connecting)) {
                    LOG.debug("Node {} is connecting", nodeId);
                } else if (status.equals(Connected)) {
                    LOG.debug("Node {} connected", nodeId);
                    futureStatus.set(true);
                    unregister();
                } else { // Unable to connect
                    LOG.warn("Unable to connect node {}", nodeId);
                    futureStatus.set(false);
                    unregister();
                }
            }
        });
    }

    private NetconfNode getNodeAugmentation(Node node) {
        NetconfNode netconfNode = node.getAugmentation(NetconfNode.class);
        if (netconfNode == null) {
            LOG.warn("Node {} is not a netconf device", node.getNodeId().getValue());
            return null;
        }
        return netconfNode;
    }

    private void unregister() {
        LOG.debug("Listener for path {} unregistered", path.getRootIdentifier());
        if (registeredListener != null) {
            registeredListener.close();
        }
    }
}
