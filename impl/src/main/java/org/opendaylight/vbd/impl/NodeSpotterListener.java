package org.opendaylight.vbd.impl;

import static org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeConnectionStatus.ConnectionStatus.Connected;
import static org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeConnectionStatus.ConnectionStatus.Connecting;

import javax.annotation.Nonnull;
import java.util.Collection;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeConnectionStatus;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class NodeSpotterListener implements ClusteredDataTreeChangeListener<Node> {

    private static final Logger LOG = LoggerFactory.getLogger(NodeSpotterListener.class);

    private final ListenerRegistration<NodeSpotterListener> registeredListener;
    private final DataTreeIdentifier<Node> path;
    private SettableFuture<Boolean> futureStatus;
    private final String TOPOLOGY_ID = "topology-netconf";

    NodeSpotterListener(final Node node, final SettableFuture<Boolean> futureStatus, final DataBroker dataBroker) {
        this.futureStatus = Preconditions.checkNotNull(futureStatus);
        Preconditions.checkArgument(!futureStatus.isDone());
        Preconditions.checkNotNull(node);
        Preconditions.checkNotNull(dataBroker);
        final InstanceIdentifier<Node> nodeIid = InstanceIdentifier.builder(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(new TopologyId(TOPOLOGY_ID)))
                .child(Node.class, node.getKey())
                .build();
        path = new DataTreeIdentifier<>(LogicalDatastoreType.OPERATIONAL, nodeIid);
        LOG.debug("Registering listener for node {}", node);
        registeredListener = dataBroker.registerDataTreeChangeListener(path, this);
    }

    @Override
    public void onDataTreeChanged(@Nonnull Collection<DataTreeModification<Node>> changes) {
        changes.forEach(modification -> {
            final DataObjectModification<Node> rootNode = modification.getRootNode();
            final Node node = rootNode.getDataAfter();
            final NetconfNode netconfNode = getNodeAugmentation(node);
            if (netconfNode == null || netconfNode.getConnectionStatus() == null) {
                LOG.warn("Node {} does not contain netconf augmentation");
                futureStatus.set(false);
                unregister();
            } else {
                final NetconfNodeConnectionStatus.ConnectionStatus status = netconfNode.getConnectionStatus();
                if (status.equals(Connecting)) {
                    LOG.debug("Node {} is connecting", node);
                } else if (status.equals(Connected)) {
                    LOG.debug("Node {} connected", node);
                    futureStatus.set(true);
                    unregister();
                } else { // Unable to connect
                    LOG.warn("Unable to connect node {}", node);
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
