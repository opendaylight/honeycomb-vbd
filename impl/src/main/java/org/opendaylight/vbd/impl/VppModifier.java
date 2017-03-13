/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vbd.impl;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.MountPointService;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vbd.impl.transaction.VbdNetconfTransaction;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4AddressNoZone;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ip.rev140616.Interface2;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ip.rev140616.interfaces.state._interface.Ipv4;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ip.rev140616.interfaces.state._interface.ipv4.Address;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.external.reference.rev160129.ExternalReference;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev161214.Loopback;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev161214.Vpp;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev161214.VppInterfaceAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev161214.VppInterfaceAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev161214.VxlanTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev161214.VxlanVni;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev161214.interfaces._interface.L2;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev161214.interfaces._interface.L2Builder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev161214.interfaces._interface.Vxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev161214.interfaces._interface.VxlanBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev161214.l2.base.attributes.Interconnection;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev161214.l2.base.attributes.interconnection.BridgeBased;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev161214.l2.base.attributes.interconnection.BridgeBasedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev161214.vpp.BridgeDomains;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev161214.vpp.bridge.domains.BridgeDomain;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev161214.vpp.bridge.domains.BridgeDomainBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev161214.vpp.bridge.domains.BridgeDomainKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.TerminationPointVbridgeAugment;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.TopologyVbridgeAugment;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.TunnelType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.network.topology.topology.TunnelParameters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.network.topology.topology.node.termination.point.InterfaceType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.network.topology.topology.node.termination.point._interface.type.UserInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.tunnel.vxlan.rev160429.TunnelTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.tunnel.vxlan.rev160429.network.topology.topology.tunnel.parameters.VxlanTunnelParameters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev161214.SubinterfaceAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev161214.interfaces._interface.SubInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev161214.interfaces._interface.sub.interfaces.SubInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev161214.interfaces._interface.sub.interfaces.SubInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  Class which is used for manipulation with VPP
 */
final class VppModifier {
    private static final Long DEFAULT_ENCAP_VRF_ID = 0L;
    private static final Short DEFAULT_SHG = 1;

    private static final Logger LOG = LoggerFactory.getLogger(VppModifier.class);
    private final MountPointService mountService;
    private final String bridgeDomainName;
    private final VbdBridgeDomain vbdBridgeDomain;
    private TopologyVbridgeAugment config;
    private final InstanceIdentifier<BridgeDomain> iiBridgeDomainOnVPP;
    private Set<NodeKey> nodesWithBridgeDomain = new HashSet<>();

    VppModifier(final MountPointService mountService, final String bridgeDomainName,
                final VbdBridgeDomain vbdBridgeDomain) {
        this.mountService = Preconditions.checkNotNull(mountService);
        this.vbdBridgeDomain = Preconditions.checkNotNull(vbdBridgeDomain);
        this.bridgeDomainName = Preconditions.checkNotNull(bridgeDomainName);
        this.iiBridgeDomainOnVPP = InstanceIdentifier.create(Vpp.class)
                .child(BridgeDomains.class)
                .child(BridgeDomain.class, new BridgeDomainKey(bridgeDomainName));
    }

    ListenableFuture<Void> deleteBridgeDomainFromVppNode(final KeyedInstanceIdentifier<Node, NodeKey> iiToVpp) {
        if (!nodesWithBridgeDomain.contains(iiToVpp.getKey())) {
            LOG.debug("Bridge domain {} not present on vpp node {} skipping...", vbdBridgeDomain, iiToVpp.getKey().getNodeId());
            return Futures.immediateFuture(null);
        }
        final DataBroker vppDataBroker = VbdUtil.resolveDataBrokerForMountPoint(iiToVpp, mountService);
        if (vppDataBroker == null) {
            LOG.warn("Got null data broker when attempting to delete bridge domain {}", bridgeDomainName);
            return Futures.immediateFuture(null);
        }
        // Remove bridge domain from interfaces
        findInterfacesWithAssignedBridgeDomain(vppDataBroker);
        final boolean transactionState = VbdNetconfTransaction.netconfSyncedDelete(vppDataBroker, this.iiBridgeDomainOnVPP,
                VbdNetconfTransaction.RETRY_COUNT);
        if (transactionState) {
            LOG.debug("Successfully deleted bridge domain {} from node {}", bridgeDomainName, PPrint.node(iiToVpp));
            nodesWithBridgeDomain.remove(iiToVpp.getKey());
        } else {
            LOG.warn("Failed to delete bridge domain {} from node {}", bridgeDomainName, PPrint.node(iiToVpp));
        }
        return Futures.immediateFuture(null);
    }

    private void deleteSupportingInterfaces(final KeyedInstanceIdentifier<Node, NodeKey> iiToVpp, final DataBroker vppDataBroker) {
        LOG.debug("Deleting interfaces supporting bridge domain {} on node {}", bridgeDomainName, PPrint.node(iiToVpp));

        final List<Interface> allInterfaces = getInterfacesFromVpp(vppDataBroker);
        final List<Interface> supportingInterfaces = getInterfacesSupportingBridgeDomain(iiToVpp, allInterfaces);

        LOG.debug("There are {} interfaces supporting bridge domain {} on node {}", supportingInterfaces.size(), bridgeDomainName, PPrint.node(iiToVpp));

        if (supportingInterfaces.isEmpty()) {
            LOG.debug("No interfaces supporting bridge domain {} on node {}. This is expected if this is the last node " +
                    "to be deleted when the bridge domain is shutting down", bridgeDomainName, PPrint.node(iiToVpp));
            return;
        }

        for (final Interface intf : supportingInterfaces) {
            deleteInterface(iiToVpp, intf, vppDataBroker);
        }

        final Optional<IpAddress> ipOp = getVtepAddress(iiToVpp, supportingInterfaces);

        if (ipOp.isPresent()) {
            deletePeerInterfaces(iiToVpp, ipOp.get());
        } else {
            LOG.warn("Unable to determine VTEP address for node {} in bridge domain {}. VXLAN tunnels facing this node cannot be deleted!", PPrint.node(iiToVpp), bridgeDomainName);
        }
    }

    private void deleteInterface(final KeyedInstanceIdentifier<Node, NodeKey> iiToVpp, final Interface intf, final DataBroker vppDataBroker) {
        LOG.debug("Deleting interface {} from config DS on vpp {}, it was supporting bridge domain {}", intf.getName(), PPrint.node(iiToVpp), bridgeDomainName);

        final KeyedInstanceIdentifier<Interface, InterfaceKey> iiToIntf = InstanceIdentifier.create(Interfaces.class).child(Interface.class, intf.getKey());
        final boolean transactionState = VbdNetconfTransaction.netconfSyncedDelete(vppDataBroker, iiToIntf,
                VbdNetconfTransaction.RETRY_COUNT);
        if (transactionState) {
            LOG.debug("Successfully deleted interface {}, which was supporting bridge domain {} on node {}",
                    intf.getName(), bridgeDomainName, PPrint.node(iiToVpp));
        } else {
            LOG.warn("Failed to delete interface {}, which was supporting bridge domain {} on node {}", intf.getName(),
                    bridgeDomainName, PPrint.node(iiToVpp));
        }
    }

    /**
     * Find the src for the vxlan tunnels for interfaces supporting the deleted node, and assume that is going to
     * be the dst address for peer interfaces which should be deleted.
     */
    private Optional<IpAddress> getVtepAddress(final KeyedInstanceIdentifier<Node, NodeKey> iiToVpp, final List<Interface> interfaces) {
        LOG.debug("Finding VTEP address for node {} on bridge domain {}", PPrint.node(iiToVpp), bridgeDomainName);
        Optional<IpAddress> vtepAddress = Optional.absent();

        for (final Interface intf : interfaces) {
            final VppInterfaceAugmentation intfAug = intf.getAugmentation(VppInterfaceAugmentation.class);

            if (intfAug == null) {
                continue;
            }

            final IpAddress tunnelSrcAddr = intfAug.getVxlan().getSrc();

            if (vtepAddress.isPresent()) {
                if (!tunnelSrcAddr.equals(vtepAddress.get())) {
                    LOG.warn("Got differing tunnel src addresses for interfaces which are supporting bridge domain " +
                            "{} on node {}. Going to assume the first one encountered ({}) is the real VTEP address. Differing address is {}",
                            bridgeDomainName, PPrint.node(iiToVpp), String.valueOf(vtepAddress.get().getValue()), String.valueOf(tunnelSrcAddr.getValue()));
                }
            } else {
                vtepAddress = Optional.of(tunnelSrcAddr);
            }
        }

        final String vtepIpStr = vtepAddress.isPresent() ? String.valueOf(vtepAddress.get().getValue()) : "UNKNOWN";
        LOG.debug("VTEP address for node {} on bridge domain {} is {}", PPrint.node(iiToVpp), bridgeDomainName, vtepIpStr);
        return vtepAddress;
    }

    private void deletePeerInterfaces(final KeyedInstanceIdentifier<Node, NodeKey> iiToVpp, final IpAddress deletedNodeAddress) {
        LOG.debug("Deleting peer interfaces for node {} in bridge domain {}. Its VTEP address is {}", PPrint.node(iiToVpp), bridgeDomainName, String.valueOf(deletedNodeAddress.getValue()));

        final List<KeyedInstanceIdentifier<Node, NodeKey>> peerIIDs = vbdBridgeDomain.getNodePeersByIID(iiToVpp);

        for (final KeyedInstanceIdentifier<Node, NodeKey> peerIID : peerIIDs) {
            final DataBroker peerDataBroker = VbdUtil.resolveDataBrokerForMountPoint(peerIID, mountService);

            if (peerDataBroker == null) {
                LOG.warn("Got null data broker for peer node {}, will not be able to remove VXLAN tunnel interfaces on this node!", PPrint.node(peerIID));
                continue;
            }

            getInterfacesFromVpp(peerDataBroker).stream()
                    .filter(peerIntf -> doesInterfacePointTowardAddress(peerIntf, deletedNodeAddress))
                    .forEach(peerIntf -> deleteInterface(peerIID, peerIntf, peerDataBroker));
        }
    }

    private boolean doesInterfacePointTowardAddress(final Interface intf, final IpAddress address) {
        final VppInterfaceAugmentation intfAug = intf.getAugmentation(VppInterfaceAugmentation.class);

        if (intfAug == null) {
            return false;
        }

        final Vxlan vxlan = intfAug.getVxlan();

        if (vxlan == null) {
            return false;
        }

        final IpAddress dst = vxlan.getDst();

        return dst != null && dst.equals(address);
    }

    private List<Interface> getInterfacesFromVpp(final DataBroker vppDataBroker) {
        // read interfaces from config DS
        final Optional<Interfaces> interfacesOptional = VbdNetconfTransaction.read(vppDataBroker, LogicalDatastoreType.CONFIGURATION,
                InstanceIdentifier.create(Interfaces.class), VbdNetconfTransaction.RETRY_COUNT);
        if (interfacesOptional.isPresent()) {
            return interfacesOptional.get().getInterface();
        }

        return Collections.emptyList();
    }

    private List<Interface> getInterfacesSupportingBridgeDomain(final KeyedInstanceIdentifier<Node, NodeKey> iiToVpp,final List<Interface> interfaces) {
        LOG.debug("Finding interfaces supporting bridge domain {} on node {}", this.bridgeDomainName, PPrint.node(iiToVpp));

        return interfaces.stream().filter(this::isInterfaceSupportingBD).collect(Collectors.toList());
    }

    /**
     * Tries to read ipv4 addresses from all specified {@code iidToVpps } vpps.
     *
     * @param iiToVpps collection of instance identifiers which points to concrete mount points.
     * @return future which contains list of ip addresses in the same order as was specified in {@code iiToVpps}
     */
    @SafeVarargs
    final List<Optional<Ipv4AddressNoZone>> readIpAddressesFromVpps(final KeyedInstanceIdentifier<Node, NodeKey>... iiToVpps) throws ExecutionException, InterruptedException {
        final List<Optional<Ipv4AddressNoZone>> ipv4Futures = new ArrayList<>(iiToVpps.length);
        for (final KeyedInstanceIdentifier<Node, NodeKey> iiToVpp : iiToVpps) {
            ipv4Futures.add(readIpAddressFromVpp(iiToVpp).get());
        }
        return ipv4Futures;
    }

    /**
     * Passes through interfaces at mount point specified via {@code iiToVpp}.
     *
     * When first ipv4 address is found then it is returned.
     *
     * @param iiToVpp instance idenfifier which point to mounted vpp
     * @return if set ipv4 address is found at mounted vpp then it is returned as future. Otherwise absent value is returned
     * in future or exception which has been thrown
     */
    private ListenableFuture<Optional<Ipv4AddressNoZone>> readIpAddressFromVpp(final KeyedInstanceIdentifier<Node, NodeKey> iiToVpp) {
        final SettableFuture<Optional<Ipv4AddressNoZone>> resultFuture = SettableFuture.create();

        final DataBroker vppDataBroker = VbdUtil.resolveDataBrokerForMountPoint(iiToVpp, mountService);
        if (vppDataBroker != null) {
            final Optional<InterfacesState> opInterfaceState = VbdNetconfTransaction.read(vppDataBroker,
                    LogicalDatastoreType.OPERATIONAL, InstanceIdentifier.create(InterfacesState.class),
                    VbdNetconfTransaction.RETRY_COUNT);
            if (opInterfaceState.isPresent()) {
                for (org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface intf : opInterfaceState.get().getInterface()) {
                    final Optional<Ipv4AddressNoZone> ipOp = readIpAddressFromInterface(intf, iiToVpp);
                    if (ipOp.isPresent() && !intf.getType().equals(Loopback.class)) {
                        resultFuture.set(ipOp);
                        break;
                    }
                }
            } else {
                LOG.debug("There appear to be no interfaces on node {}.", PPrint.node(iiToVpp));
            }

            // if we got here, we were unable to successfully read an ip address from any of the node's interfaces
            resultFuture.set(Optional.absent());
        } else {
            LOG.debug("Data broker for vpp {} is missing.", iiToVpp);
        }
        return resultFuture;
    }

    /**
     * Read the first available IPv4 address from the given interface.
     *
     * @param intf Interface to read an address from
     * @param iiToVpp Node which contains the given interface
     * @return An optional which is set to the IPv4 address which was read from the interface. If no IPv4 address could
     * be read from this interface, the optional will be absent.
     */
    private Optional<Ipv4AddressNoZone>
    readIpAddressFromInterface(final org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface intf,
                               final KeyedInstanceIdentifier<Node, NodeKey> iiToVpp) {
        final Interface2 augIntf = intf.getAugmentation(Interface2.class);

        if (augIntf == null) {
            LOG.debug("Cannot get Interface2 augmentation for intf {}", intf);
            return Optional.absent();
        }

        final Ipv4 ipv4 = augIntf.getIpv4();
        if (ipv4 == null) {
            LOG.debug("Ipv4 address for interface {} on node {} is null!", augIntf, PPrint.node(iiToVpp));
            return Optional.absent();
        }

        final List<Address> addresses = ipv4.getAddress();
        if (addresses == null || addresses.isEmpty()) {
            LOG.debug("Ipv4 addresses list is empty for interface {} on node {}", augIntf, PPrint.node(iiToVpp));
            return Optional.absent();
        }

        final Ipv4AddressNoZone ip = addresses.iterator().next().getIp();
        if (ip == null) {
            LOG.debug("Ipv4AddressNoZone is null for node {}", PPrint.node(iiToVpp));
            return Optional.absent();
        }

        LOG.debug("Got ip address {} from interface {} on node {}", ip.getValue(), intf.getName(), PPrint.node(iiToVpp));
        return Optional.of(ip);
    }

    /**
     * Create virtual interface and add appropriate bridge domain. If interface already exists
     * (found by {@link VppModifier#findVxlanTunnelFromIpAddresses(Ipv4AddressNoZone, Ipv4AddressNoZone, VxlanVni,
     * DataBroker)}), only bridge domain is leveraged from input data and written into interface. If interface is
     * missing, it's written with bridge domain set up.
     *
     * @param ipSrc         source ip address
     * @param ipDst         destination ip address
     * @param iidToVpp      {@link InstanceIdentifier} to node
     * @param vxlanTunnelId input id of vxlan tunnel. This id is used only when particular interface does not exist
     */
    ListenableFuture<Void> createVirtualInterfaceOnVpp(final Ipv4AddressNoZone ipSrc, final Ipv4AddressNoZone ipDst,
                                                       final VxlanVni currentBdVni,
                                                       final KeyedInstanceIdentifier<Node, NodeKey> iidToVpp,
                                                       final Integer vxlanTunnelId) {
        final DataBroker vppDataBroker = VbdUtil.resolveDataBrokerForMountPoint(iidToVpp, mountService);
        if (vppDataBroker == null) {
            LOG.warn("Writing virtual interface {} to VPP {} wasn't successful because data broker is missing",
                    VbdUtil.provideVxlanId(vxlanTunnelId), iidToVpp);
            return Futures.immediateFuture(null);
        }
        final Vxlan vxlanData = prepareVxlan(ipSrc, ipDst);
        final Integer potentialExistingInterfaceTunnelId =
                findVxlanTunnelFromIpAddresses(ipSrc, ipDst, currentBdVni, vppDataBroker);
        // Interface exists, add BD only
        if (potentialExistingInterfaceTunnelId != null) {
            LOG.debug("Interface with srcIp {} and dstIp {} found, bridge domain will be added.", ipSrc, ipDst);
            final Interface interfaceData = prepareVirtualInterfaceData(vxlanData, vxlanTunnelId);
            final L2 l2Data = interfaceData.getAugmentation(VppInterfaceAugmentation.class).getL2();
            LOG.trace("L2 data: {}", l2Data);
            final String vxlanId = VbdUtil.provideVxlanId(potentialExistingInterfaceTunnelId);
            final InstanceIdentifier<L2> l2Iid = InstanceIdentifier.create(Interfaces.class).child(Interface.class,
                    new InterfaceKey(vxlanId)).augmentation(VppInterfaceAugmentation.class).child(L2.class).builder().build();
            boolean transactionState = VbdNetconfTransaction.netconfSyncedWrite(vppDataBroker, l2Iid, l2Data,
                    VbdNetconfTransaction.RETRY_COUNT);
            if (transactionState) {
                LOG.debug("Writing bridge domain into super virtual interface to {} finished successfully.",
                        PPrint.node(iidToVpp));
            } else {
                LOG.warn("Writing bridge domain into super virtual interface to {} failed.", PPrint.node(iidToVpp));
            }
        }
        // Interface does not exist, create a new one with BD assigned
        else {
            LOG.debug("Interface with srcIp {} and dstIp {} not found, creating a new one with bridge domain attached.",
                    ipSrc, ipDst);
            final Interface interfaceData = prepareVirtualInterfaceData(vxlanData, vxlanTunnelId);
            LOG.trace("Interface data: {}", interfaceData);
            final KeyedInstanceIdentifier<Interface, InterfaceKey> iidToInterface
                    = InstanceIdentifier.create(Interfaces.class).child(Interface.class,
                    new InterfaceKey(VbdUtil.provideVxlanId(vxlanTunnelId)));
            LOG.debug("Submitting new interface to config store...");
            boolean transactionState = VbdNetconfTransaction.netconfSyncedWrite(vppDataBroker, iidToInterface, interfaceData,
                    VbdNetconfTransaction.RETRY_COUNT);
            if (transactionState) {
                LOG.debug("Writing super virtual interface to {} finished successfully.", PPrint.node(iidToVpp));
            } else {
                LOG.warn("Writing super virtual interface to {} failed.", PPrint.node(iidToVpp));
            }
        }
        return Futures.immediateFuture(null);
    }

    /**
     * Get all interfaces from vpp, selects vxlan tunnels and finds the one with matching source ip address, destination
     * ip address and VNI. Returns vxlan tunnel index or null if such a interface is not present on node mountpoint
     * belongs to
     *
     * @param srcIp      source ip address
     * @param dstIp      destination ip address
     * @param currentBdVni actual bridge domain vni value
     * @param mountpoint to access vpp router
     * @return vxlan tunnel id as an String, null otherwise
     */
    @Nullable
    private Integer findVxlanTunnelFromIpAddresses(final Ipv4AddressNoZone srcIp, final Ipv4AddressNoZone dstIp,
                                                   final VxlanVni currentBdVni, final DataBroker mountpoint) {
        final InstanceIdentifier<Interfaces> interfacesIid = InstanceIdentifier.create(Interfaces.class);
        final Optional<Interfaces> optionalInterfaces = VbdNetconfTransaction.read(mountpoint,
                LogicalDatastoreType.CONFIGURATION, interfacesIid, VbdNetconfTransaction.RETRY_COUNT);
        if (!optionalInterfaces.isPresent()) {
            LOG.warn("No interfaces exist for device {}", mountpoint);
            return null;
        }
        final Interfaces availableInterfaces = optionalInterfaces.get();
        // Find vxlan tunnels
        final List<Interface> interfaces = availableInterfaces.getInterface();
        if (interfaces == null || interfaces.isEmpty()) {
            LOG.warn("No vxlan tunnel is available for source ip: {} and destination ip: {}", srcIp, dstIp);
            return null;
        }
        for (Interface potentialVxlan : interfaces) {
            if (potentialVxlan.getType() != null && potentialVxlan.getType().equals(VxlanTunnel.class)) {
                // Get augmentation
                final VppInterfaceAugmentation augmentation = potentialVxlan.getAugmentation(VppInterfaceAugmentation.class);
                if (augmentation != null && augmentation.getVxlan() != null) {
                    final Vxlan vxlan = augmentation.getVxlan();
                    // Resolve ip addresses, only Ipv4 addresses are supported
                    final Ipv4AddressNoZone vxlanSrcIp = new Ipv4AddressNoZone(vxlan.getSrc().getIpv4Address());
                    final Ipv4AddressNoZone vxlanDstIp = new Ipv4AddressNoZone(vxlan.getDst().getIpv4Address());
                    final VxlanVni vni = vxlan.getVni();
                    if (vni == null) {
                        LOG.warn("Cannot verify VNI for vxlan tunnel {}, provided vni is null", vxlan);
                        continue;
                    }
                    if (srcIp.equals(vxlanSrcIp) && dstIp.equals(vxlanDstIp) && vni.equals(currentBdVni)) {
                        // Desired vxlan tunnel found, get tunnel id
                        String vxlanName = potentialVxlan.getName();
                        // Replace any non-digit chars with empty space to get tunnel id
                        return Integer.valueOf(vxlanName.replaceAll("\\D+", ""));
                    }
                }
            }
        }
        LOG.debug("Vxlan tunnel was not found for src ip: {} and dst ip: {}", srcIp, dstIp);
        return null;
    }

    private Interface prepareVirtualInterfaceData(final Vxlan vxlan, Integer vxlanTunnelId) {
        final InterfaceBuilder interfaceBuilder = new InterfaceBuilder();
        //TODO implement tunnel counter
        interfaceBuilder.setName(VbdUtil.provideVxlanId(vxlanTunnelId));
        interfaceBuilder.setType(VxlanTunnel.class);
        VppInterfaceAugmentationBuilder vppInterfaceAugmentationBuilder = new VppInterfaceAugmentationBuilder();
        vppInterfaceAugmentationBuilder.setVxlan(vxlan);
        vppInterfaceAugmentationBuilder.setL2(prepareL2Data(false, DEFAULT_SHG));
        interfaceBuilder.addAugmentation(VppInterfaceAugmentation.class, vppInterfaceAugmentationBuilder.build());
        interfaceBuilder.setEnabled(true);
        return interfaceBuilder.build();
    }

    private Vxlan prepareVxlan(final Ipv4AddressNoZone ipSrc, final Ipv4AddressNoZone ipDst) {
        final VxlanBuilder vxlanBuilder = new VxlanBuilder();
        vxlanBuilder.setSrc(new IpAddress(ipSrc));
        vxlanBuilder.setDst(new IpAddress(ipDst));
        final TunnelParameters tunnelParameters = config.getTunnelParameters();
        final Class<? extends TunnelType> tunnelType = config.getTunnelType();
        if (tunnelType.equals(TunnelTypeVxlan.class)) {
            if (tunnelParameters instanceof VxlanTunnelParameters) {
                final VxlanTunnelParameters vxlanTunnelParams = (VxlanTunnelParameters) tunnelParameters;
                final VxlanVni vni = vxlanTunnelParams.getVni();

                if (vni != null) {
                    vxlanBuilder.setVni(vni);
                } else {
                    LOG.warn("Tunnel type is VXLAN but no VNI parameter was given! Creating new vxlan without VNI, result is undefined!");
                }
            } else {
                LOG.warn("Tunnel type is vxlan but tunnel parameters are not for vxlan!?!?");
            }
        }
        vxlanBuilder.setEncapVrfId(DEFAULT_ENCAP_VRF_ID);
        return vxlanBuilder.build();
    }

    ListenableFuture<Void> addInterfaceToBridgeDomainOnVpp(final DataBroker vppDataBroker, final TerminationPointVbridgeAugment termPointVbridgeAug) {
        final InterfaceType interfaceType = termPointVbridgeAug.getInterfaceType();
        if (interfaceType instanceof UserInterface) {
            //REMARK: according contract in YANG model this should be URI to data on mount point (according to RESTCONF)
            //It was much more easier to just await concrete interface name, thus isn't necessary parse it (splitting on '/')
            final ExternalReference userInterface = ((UserInterface) interfaceType).getUserInterface();
            final KeyedInstanceIdentifier<Interface, InterfaceKey> iiToVpp =
                    InstanceIdentifier.create(Interfaces.class)
                            .child(Interface.class, new InterfaceKey(userInterface.getValue()));
            InstanceIdentifier<L2> iiToV3poL2 = iiToVpp.augmentation(VppInterfaceAugmentation.class).child(L2.class);
            LOG.debug("Writing L2 data to configuration DS to concrete interface.");
            VbdNetconfTransaction.netconfSyncedWrite(vppDataBroker, iiToV3poL2,  prepareL2Data(false, null),
                    VbdNetconfTransaction.RETRY_COUNT);
        }
        return Futures.immediateFuture(null);
    }

    ListenableFuture<Void> addVppToBridgeDomain(final KeyedInstanceIdentifier<Node, NodeKey> iiToVpp) {
        final DataBroker vppDataBroker = VbdUtil.resolveDataBrokerForMountPoint(iiToVpp, mountService);
        if (vppDataBroker != null) {
            VbdNetconfTransaction.netconfSyncedWrite(vppDataBroker, iiBridgeDomainOnVPP, prepareNewBridgeDomainData(),
                    VbdNetconfTransaction.RETRY_COUNT);
            nodesWithBridgeDomain.add(iiToVpp.getKey());
            return Futures.immediateFuture(null);
        }
        return Futures.immediateFailedFuture(new IllegalStateException("Data broker for vpp is missing"));
    }

    private BridgeDomain prepareNewBridgeDomainData() {
        final BridgeDomainBuilder bridgeDomainBuilder = new BridgeDomainBuilder(config);
        bridgeDomainBuilder.setName(bridgeDomainName);
        return bridgeDomainBuilder.build();
    }

    /**
     * Prepare the L2 interface fields from the VPP interface augmentation in the v3po model.
     *
     * @param bridgedVirtualInterface value for the bridged-virtual-interface field
     * @return the built L2 object
     */
    private L2 prepareL2Data(final boolean bridgedVirtualInterface, final Short splitHgrp ) {
        final L2Builder l2Builder = new L2Builder();
        final BridgeBasedBuilder bridgeBasedBuilder = new BridgeBasedBuilder();
        bridgeBasedBuilder.setBridgedVirtualInterface(bridgedVirtualInterface);
        bridgeBasedBuilder.setBridgeDomain(bridgeDomainName);
        if (splitHgrp!=null) {
            bridgeBasedBuilder.setSplitHorizonGroup(splitHgrp);
        }
        l2Builder.setInterconnection(bridgeBasedBuilder.build());
        return l2Builder.build();
    }

    public void setConfig(final TopologyVbridgeAugment config) {
        this.config = config;
    }

    private boolean isInterfaceSupportingBD(final Interface intf) {
        final VppInterfaceAugmentation vppInterface = intf.getAugmentation(VppInterfaceAugmentation.class);
        if(vppInterface == null) {
            return false;
        }

        final L2 l2Data = vppInterface.getL2();

        if (l2Data == null) {
            return false;
        }

        final Interconnection interconnection = l2Data.getInterconnection();

        if (!(interconnection instanceof BridgeBased)) {
            return false;
        }

        LOG.debug("Got bridge based VPP interface {}", intf.getName());

        return  VxlanTunnel.class.equals(intf.getType())
                && this.bridgeDomainName.equals(((BridgeBased) interconnection).getBridgeDomain());
    }

    ListenableFuture<Void> deleteVxlanInterface(final Ipv4AddressNoZone srcIp, final Ipv4AddressNoZone dstIp,
                                                final VxlanVni currentBdVni,
                                                final KeyedInstanceIdentifier<Node, NodeKey> vppNodeIid) {
        final DataBroker vppDataBroker = VbdUtil.resolveDataBrokerForMountPoint(vppNodeIid, mountService);
        if (vppDataBroker == null) {
            LOG.warn("Mountpoint not found for node {}", vppNodeIid);
            return Futures.immediateFuture(null);
        }
        final Integer tunnelId = findVxlanTunnelFromIpAddresses(srcIp, dstIp, currentBdVni, vppDataBroker);
        if (tunnelId == null) {
            LOG.debug("Vxlan tunnel with source ip {}, destination ip {} and bridge domain {} not found on node {}",
                    srcIp, dstIp, bridgeDomainName, vppNodeIid);
            return Futures.immediateFuture(null);
        }
        final String vxlanId = VbdUtil.provideVxlanId(tunnelId);
        final InstanceIdentifier<Interface> interfaceId = InstanceIdentifier.create(Interfaces.class).child(Interface.class,
                new InterfaceKey(vxlanId)).builder().build();
        LOG.debug("Removing bridge domain from vxlan {} on node {}", vxlanId, vppNodeIid);
        boolean transactionState = VbdNetconfTransaction.netconfSyncedDelete(vppDataBroker, interfaceId,
                VbdNetconfTransaction.RETRY_COUNT);
        if (transactionState) {
            LOG.debug("Bridge domain successfully removed from vxlan interface on node {}", vppNodeIid);
        } else {
            LOG.warn("Failed to remove bridge domain from interface. Node: {}, cause: {}", vppNodeIid);
        }
        return Futures.immediateFuture(null);
    }

    /**
     * Called right before particular bridge domain is removed. Method reads all interfaces from vpp node and finds
     * every one which is connected to bridge domain marked to delete. If such a port is found, the whole L2
     * augmentation is removed from that port. Removal of port itself is done in groupbasedpolicy project.
     *
     * @param vppDataBroker points to vppNode from which bridge domain will be removed
     */
    private void findInterfacesWithAssignedBridgeDomain(final DataBroker vppDataBroker) {
        LOG.debug("Reading interfaces assigned to bridge domain {} marked to delete", bridgeDomainName);
        final InstanceIdentifier<Interfaces> interfacesIid = InstanceIdentifier.create(Interfaces.class).builder().build();
        final Optional<Interfaces> optionalInterfaces = VbdNetconfTransaction.read(vppDataBroker,
                LogicalDatastoreType.CONFIGURATION, interfacesIid, VbdNetconfTransaction.RETRY_COUNT);
        if (optionalInterfaces.isPresent()) {
            final Interfaces interfaces = optionalInterfaces.get();
            final List<Interface> interfaceList = interfaces.getInterface();
            for (final Interface bdInterface : interfaceList) {
                final VppInterfaceAugmentation interfaceAugmentation =
                        bdInterface.getAugmentation(VppInterfaceAugmentation.class);
                final SubinterfaceAugmentation subInterfaceAugmentation =
                        bdInterface.getAugmentation(SubinterfaceAugmentation.class);
                // Vxlan case
                if (interfaceAugmentation != null && interfaceAugmentation.getL2() != null) {
                    final L2 l2 = interfaceAugmentation.getL2();
                    final Interconnection interconnection = l2.getInterconnection();
                    if (interconnection != null && interconnection instanceof BridgeBased) {
                        final BridgeBased bridgeBased = (BridgeBased) interconnection;
                        if (bridgeDomainName.equals(bridgeBased.getBridgeDomain())) {
                            LOG.trace("Interface {} has assigned bd which will be removed. Disconnecting ...",
                                    bdInterface.getName());
                            final InstanceIdentifier<L2> augmentationIid = InstanceIdentifier.create(Interfaces.class)
                                    .child(Interface.class, bdInterface.getKey()).augmentation(VppInterfaceAugmentation.class)
                                    .child(L2.class).builder().build();
                            VbdNetconfTransaction.netconfSyncedDelete(vppDataBroker, augmentationIid,
                                VbdNetconfTransaction.RETRY_COUNT);
                            LOG.trace("Bridge domain {} removed from interface {}", bridgeDomainName, bdInterface.getName());
                        }
                    }
                }
                // Vlan case
                if (subInterfaceAugmentation != null) {
                    final SubInterfaces subInterfaces = subInterfaceAugmentation.getSubInterfaces();
                    for (SubInterface subInterface : subInterfaces.getSubInterface()) {
                        final org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev161214.sub._interface.base.attributes.L2 l2 = subInterface.getL2();
                        if (l2 != null && l2.getInterconnection() != null) {
                            final Interconnection interconnection = l2.getInterconnection();
                            if (interconnection instanceof BridgeBased) {
                                final BridgeBased bridgeBased = (BridgeBased) interconnection;
                                if (bridgeDomainName.equals(bridgeBased.getBridgeDomain())) {
                                    LOG.trace("Sub-interface {} has assigned bd which will be removed. Disconnecting ...",
                                            bdInterface.getName());
                                    final InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev161214.sub._interface.base.attributes.L2> augmentationIid = InstanceIdentifier.create(Interfaces.class)
                                            .child(Interface.class, bdInterface.getKey()).augmentation(SubinterfaceAugmentation.class).child(SubInterfaces.class)
                                            .child(SubInterface.class, new SubInterfaceKey(subInterface.getKey()))
                                            .child(org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev161214.sub._interface.base.attributes.L2.class).builder().build();
                                    VbdNetconfTransaction.netconfSyncedDelete(vppDataBroker, augmentationIid,
                                        VbdNetconfTransaction.RETRY_COUNT);
                                    LOG.trace("Bridge domain {} removed from sub-interface {}", bridgeDomainName, bdInterface.getName());
                                }
                            }
                        }
                    }
                }

            }
            return;
        }
        LOG.error("No interfaces assigned to bridge domain {} found");
    }
}
