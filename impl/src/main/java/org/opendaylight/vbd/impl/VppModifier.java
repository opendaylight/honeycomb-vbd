/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vbd.impl;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.MountPointService;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.ReadTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev150105.Vpp;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev150105.VppInterfaceAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev150105.VppInterfaceAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev150105.VxlanTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev150105.VxlanVni;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev150105.interfaces._interface.L2;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev150105.interfaces._interface.L2Builder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev150105.interfaces._interface.Vxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev150105.interfaces._interface.VxlanBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev150105.l2.base.attributes.Interconnection;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev150105.l2.base.attributes.interconnection.BridgeBased;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev150105.l2.base.attributes.interconnection.BridgeBasedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev150105.vpp.BridgeDomains;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev150105.vpp.bridge.domains.BridgeDomainBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev150105.vpp.bridge.domains.BridgeDomainKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.TerminationPointVbridgeAugment;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.TopologyVbridgeAugment;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.TunnelType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.network.topology.topology.TunnelParameters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.network.topology.topology.node.termination.point.InterfaceType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.network.topology.topology.node.termination.point._interface.type.UserInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.tunnel.vxlan.rev160429.TunnelTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.tunnel.vxlan.rev160429.network.topology.topology.tunnel.parameters.VxlanTunnelParameters;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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
    private final InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev150105.vpp.bridge.domains.BridgeDomain> iiBridgeDomainOnVPP;

    VppModifier(final MountPointService mountService, final String bridgeDomainName, final VbdBridgeDomain vbdBridgeDomain) {
        this.mountService = mountService;
        this.vbdBridgeDomain = vbdBridgeDomain;
        this.bridgeDomainName = bridgeDomainName;
        this.iiBridgeDomainOnVPP = InstanceIdentifier.create(Vpp.class)
                .child(BridgeDomains.class)
                .child(org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev150105.vpp.bridge.domains.BridgeDomain.class, new BridgeDomainKey(bridgeDomainName));
    }

    Optional<ListenableFuture<Void>> deleteBridgeDomain(final KeyedInstanceIdentifier<Node, NodeKey> iiToVpp) {
        final DataBroker vppDataBroker = VbdUtil.resolveDataBrokerForMountPoint(iiToVpp, mountService);

        if (vppDataBroker == null) {
            LOG.warn("Got null data broker when attempting to delete bridge domain {}", bridgeDomainName);
            return Optional.absent();
        }

        deleteSupportingInterfaces(iiToVpp, vppDataBroker);

        final WriteTransaction wTx = vppDataBroker.newWriteOnlyTransaction();

        wTx.delete(LogicalDatastoreType.CONFIGURATION, this.iiBridgeDomainOnVPP);

        final ListenableFuture<Void> txResult = wTx.submit();

        Futures.addCallback(txResult, new FutureCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void result) {
                LOG.debug("Successfully deleted bridge domain {} from node {}", bridgeDomainName, PPrint.node(iiToVpp));
            }

            @Override
            public void onFailure(@Nonnull Throwable t) {
                LOG.warn("Failed to delete bridge domain {} from node {}", bridgeDomainName, PPrint.node(iiToVpp), t);
            }
        });

        return Optional.of(txResult);
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
        final WriteTransaction wTx = vppDataBroker.newWriteOnlyTransaction();

        wTx.delete(LogicalDatastoreType.CONFIGURATION, iiToIntf);

        Futures.addCallback(wTx.submit(), new FutureCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void result) {
                LOG.debug("Successfully deleted interface {}, which was supporting bridge domain {} on node {}", intf.getName(), bridgeDomainName, PPrint.node(iiToVpp));
            }

            @Override
            public void onFailure(@Nonnull Throwable t) {
                LOG.warn("Failed to delete interface {}, which was supporting bridge domain {} on node {}", intf.getName(), bridgeDomainName, PPrint.node(iiToVpp), t);
            }
        });
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
        final ReadOnlyTransaction rTx = vppDataBroker.newReadOnlyTransaction();

        final Optional<Interfaces> interfacesOptional = readFromDs(LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(Interfaces.class), rTx);

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
     * Tryies to read ipv4 addresses from all specified {@code iiToVpps } vpps.
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
            final ReadOnlyTransaction rTx = vppDataBroker.newReadOnlyTransaction();
            final CheckedFuture<Optional<InterfacesState>, ReadFailedException> interfaceStateFuture
                    = rTx.read(LogicalDatastoreType.OPERATIONAL, InstanceIdentifier.create(InterfacesState.class));

            Futures.addCallback(interfaceStateFuture, new FutureCallback<Optional<InterfacesState>>() {
                @Override
                public void onSuccess(final Optional<InterfacesState> optInterfaces) {
                    if (optInterfaces.isPresent()) {
                        for (org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface intf : optInterfaces.get().getInterface()) {
                            final Optional<Ipv4AddressNoZone> ipOp = readIpAddressFromInterface(intf, iiToVpp);
                            if (ipOp.isPresent()) {
                                resultFuture.set(ipOp);
                                return;
                            }
                        }
                    } else {
                        LOG.debug("There appear to be no interfaces on node {}.", PPrint.node(iiToVpp));
                    }

                    // if we got here, we were unable to successfully read an ip address from any of the node's interfaces
                    resultFuture.set(Optional.absent());
                }

                @Override
                public void onFailure(@Nonnull Throwable t) {
                    resultFuture.setException(t);
                }
            });
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
            LOG.trace("Cannot get Interface2 augmentation for intf {}", intf);
            return Optional.absent();
        }

        final Ipv4 ipv4 = augIntf.getIpv4();
        if (ipv4 == null) {
            LOG.debug("Ipv4 address for interface {} on node {} is null!", augIntf, PPrint.node(iiToVpp));
            return Optional.absent();
        }

        final List<Address> addresses = ipv4.getAddress();
        if (addresses == null || addresses.isEmpty()) {
            LOG.debug("Ipv4 addresses list is empty for intf {} on node {}", augIntf, PPrint.node(iiToVpp));
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

    void createVirtualInterfaceOnVpp(final Ipv4AddressNoZone ipSrc, final Ipv4AddressNoZone ipDst, final KeyedInstanceIdentifier<Node, NodeKey> iiToVpp,
                                     final Integer vxlanTunnelId) {
        final Vxlan vxlanData = prepareVxlan(ipSrc, ipDst);
        final Interface intfData = prepareVirtualInterfaceData(vxlanData, vxlanTunnelId);

        LOG.debug("Creating virtual interface ({}) on vpp {} for vxlan tunnel ({} -> {}, id: {})", intfData.getKey().getName(), iiToVpp.getKey().getNodeId().getValue(), ipSrc.getValue(), ipDst.getValue(), vxlanTunnelId);
        final DataBroker vppDataBroker = VbdUtil.resolveDataBrokerForMountPoint(iiToVpp, mountService);
        if (vppDataBroker != null) {
            final WriteTransaction wTx = vppDataBroker.newWriteOnlyTransaction();
            final KeyedInstanceIdentifier<Interface, InterfaceKey> iiToInterface
                    = InstanceIdentifier.create(Interfaces.class).child(Interface.class, new InterfaceKey(VbdUtil.provideVxlanId(vxlanTunnelId)));
            wTx.put(LogicalDatastoreType.CONFIGURATION, iiToInterface, intfData);

            LOG.debug("Submitting new interface to config store...");

            Futures.addCallback(wTx.submit(), new FutureCallback<Void>() {
                @Override
                public void onSuccess(@Nullable Void result) {
                    LOG.debug("Writing super virtual interface to {} finished successfully.", PPrint.node(iiToVpp));
                }

                @Override
                public void onFailure(@Nonnull Throwable t) {
                    LOG.warn("Writing super virtual interface to {} failed.", PPrint.node(iiToVpp), t);
                }
            });
        } else {
            LOG.warn("Writing virtual interface {} to VPP {} wasn't successful because data broker is missing", VbdUtil.provideVxlanId(vxlanTunnelId), iiToVpp);
        }
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

    void addInterfaceToBridgeDomainOnVpp(final DataBroker vppDataBroker, final TerminationPointVbridgeAugment termPointVbridgeAug) {
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
            final WriteTransaction wTx = vppDataBroker.newWriteOnlyTransaction();
            wTx.put(LogicalDatastoreType.CONFIGURATION, iiToV3poL2, prepareL2Data(false, null), true);
            wTx.submit();
        }
    }

    ListenableFuture<Void> addVppToBridgeDomain(final KeyedInstanceIdentifier<Node, NodeKey> iiToVpp) {
        final DataBroker vppDataBroker = VbdUtil.resolveDataBrokerForMountPoint(iiToVpp, mountService);
        if (vppDataBroker != null) {
            final WriteTransaction wTx = vppDataBroker.newWriteOnlyTransaction();
            wTx.put(LogicalDatastoreType.CONFIGURATION, iiBridgeDomainOnVPP, prepareNewBridgeDomainData(), true);
            return wTx.submit();
        }
        return Futures.immediateFailedFuture(new IllegalStateException("Data broker for vpp is missing"));
    }

    private org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev150105.vpp.bridge.domains.BridgeDomain
    prepareNewBridgeDomainData() {
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

    /**
     * Reads data from datastore as synchrone call.
     * @return {@link Optional#isPresent()} is {@code true} if reading was successful and data exists in datastore; {@link Optional#isPresent()} is {@code false} otherwise
     */
    public static <T extends DataObject> Optional<T> readFromDs(LogicalDatastoreType store, InstanceIdentifier<T> path, ReadTransaction rTx) {
        CheckedFuture<Optional<T>, ReadFailedException> resultFuture = rTx.read(store, path);
        try {
            return resultFuture.checkedGet();
        } catch (ReadFailedException e) {
            LOG.warn("Read failed from DS.", e);
            return Optional.absent();
        }
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
}
