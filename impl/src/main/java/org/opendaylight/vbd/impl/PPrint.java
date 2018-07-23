/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vbd.impl;

import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;

/**
 * Utility methods for dealing with Topology objects.
 */

public class PPrint {
    public static String topology(final KeyedInstanceIdentifier<Topology, TopologyKey> topoIID) {
        return topoIID.getKey().getTopologyId().getValue();
    }

    public static String topology(final Topology topology) {
        return topology.key().getTopologyId().getValue();
    }

    public static String node(final KeyedInstanceIdentifier<Node, NodeKey> nodeIID) {
        return nodeIID.getKey().getNodeId().getValue();
    }

}
