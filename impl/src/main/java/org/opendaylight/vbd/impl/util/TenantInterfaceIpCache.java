/*
 * Copyright (c) 2017 Cisco Systems. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vbd.impl.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4AddressNoZone;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;

import com.google.common.base.Optional;

public final class TenantInterfaceIpCache {

    private Map<KeyedInstanceIdentifier<Node, NodeKey>, Optional<Ipv4AddressNoZone>> map;

    private static final TenantInterfaceIpCache INSTANCE = new TenantInterfaceIpCache();

    public static TenantInterfaceIpCache getInstance() {
        return INSTANCE;
    }

    private TenantInterfaceIpCache() {
        map = new ConcurrentHashMap<>();
    }

    public void put(@Nonnull KeyedInstanceIdentifier<Node, NodeKey> key, @Nonnull Optional<Ipv4AddressNoZone> value) {
        map.put(key, value);
    }

    public Optional<Ipv4AddressNoZone> get(KeyedInstanceIdentifier<Node, NodeKey> key) {
        return map.get(key);
    }

    public boolean containsKey(KeyedInstanceIdentifier<Node, NodeKey> key) {
        return map.containsKey(key);
    }
}
