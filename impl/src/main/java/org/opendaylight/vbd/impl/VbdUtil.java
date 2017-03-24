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
import java.util.Collections;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.MountPoint;
import org.opendaylight.controller.md.sal.binding.api.MountPointService;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.OptimisticLockFailedException;
import org.opendaylight.yang.gen.v1.urn.ieee.params.xml.ns.yang.dot1q.types.rev150626.Dot1qVlanId;
import org.opendaylight.yang.gen.v1.urn.ieee.params.xml.ns.yang.dot1q.types.rev150626.SVlan;
import org.opendaylight.yang.gen.v1.urn.ieee.params.xml.ns.yang.dot1q.types.rev150626.dot1q.tag.or.any.Dot1qTag;
import org.opendaylight.yang.gen.v1.urn.ieee.params.xml.ns.yang.dot1q.types.rev150626.dot1q.tag.or.any.Dot1qTagBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.VlanId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.external.reference.rev160129.ExternalReference;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev170315.BridgeDomainsState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev170315.VxlanVni;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev170315.l2.base.attributes.interconnection.BridgeBasedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev170315.BridgeDomains;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev170315.bridge.domains.state.BridgeDomain;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev170315.bridge.domains.state.BridgeDomainBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev170315.bridge.domains.state.BridgeDomainKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.status.rev161005.BridgeDomainStatusAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.status.rev161005.BridgeDomainStatusAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.status.rev161005.BridgeDomainStatusFields;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.LinkVbridgeAugment;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.LinkVbridgeAugmentBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.TopologyTypesVbridgeAugment;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.TopologyTypesVbridgeAugmentBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.TopologyVbridgeAugment;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.TunnelType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.network.topology.topology.TunnelParameters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.topology.rev160129.network.topology.topology.topology.types.VbridgeTopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.tunnel.vlan.rev160429.TunnelTypeVlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.tunnel.vlan.rev160429.network.topology.topology.tunnel.parameters.VlanNetworkParameters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.tunnel.vxlan.rev160429.TunnelTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.tunnel.vxlan.rev160429.network.topology.topology.tunnel.parameters.VxlanTunnelParameters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev170315.VlanType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev170315.interfaces._interface.sub.interfaces.SubInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev170315.interfaces._interface.sub.interfaces.SubInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev170315.interfaces._interface.sub.interfaces.SubInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev170315.match.attributes.match.type.VlanTagged;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev170315.match.attributes.match.type.VlanTaggedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev170315.sub._interface.base.attributes.L2;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev170315.sub._interface.base.attributes.L2Builder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev170315.sub._interface.base.attributes.Match;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev170315.sub._interface.base.attributes.MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev170315.sub._interface.base.attributes.Tags;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev170315.sub._interface.base.attributes.TagsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev170315.sub._interface.base.attributes.l2.RewriteBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev170315.sub._interface.base.attributes.tags.TagBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.LinkId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TpId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.link.attributes.DestinationBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.link.attributes.SourceBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.LinkBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.TopologyTypes;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.TopologyTypesBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPointKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class VbdUtil {

    public static final TopologyId STARTUP_CONFIG_TOPOLOGY = new TopologyId("vbridge-startup-config"); // see vbridge-topology.yang

    private static final Logger LOG = LoggerFactory.getLogger(VbdUtil.class);
    private static final String TUNNEL_ID_PREFIX = "vxlan_tunnel";
    private static final String BRIDGE_DOMAIN_IID_PREFIX = "v3po:vpp/bridge-domains/bridge-domain/";

    private VbdUtil() {
        throw new UnsupportedOperationException("Can't instantiate util class");
    }

    public static InstanceIdentifierBuilder<Topology> topologyIid(TopologyId topologyId) {
        return InstanceIdentifier.builder(NetworkTopology.class)
            .child(Topology.class, new TopologyKey(topologyId));
    }

    public static InstanceIdentifierBuilder<TerminationPoint> terminationPointIid(TopologyId topologyId, NodeId nodeId, TpId tpId) {
        return topologyIid(topologyId).child(Node.class, new NodeKey(new NodeId(nodeId)))
            .child(TerminationPoint.class, new TerminationPointKey(tpId));
    }

    static DataBroker resolveDataBrokerForMountPoint(final InstanceIdentifier<Node> iiToMountPoint, final MountPointService mountService) {
        final Optional<MountPoint> vppMountPointOpt = mountService.getMountPoint(iiToMountPoint);
        if (vppMountPointOpt.isPresent()) {
            final MountPoint vppMountPoint = vppMountPointOpt.get();
            final Optional<DataBroker> dataBrokerOpt = vppMountPoint.getService(DataBroker.class);
            if (dataBrokerOpt.isPresent()) {
                return dataBrokerOpt.get();
            }
        }
        return null;
    }

    static Topology buildFreshTopology(final KeyedInstanceIdentifier<Topology, TopologyKey> topoIID) {
        final TopologyTypes topoType = new TopologyTypesBuilder()
                .addAugmentation(TopologyTypesVbridgeAugment.class, createVbridgeTopologyType()).build();

        final TopologyBuilder tb = new TopologyBuilder();

        tb.setKey(topoIID.getKey())
                .setTopologyId(topoIID.getKey().getTopologyId())
                .setTopologyTypes(topoType);

        return tb.build();
    }

    static SubInterface createSubInterface(final VlanId vlan,
                                           final Class<? extends VlanType> vlanType,
                                           final String bridgeDomainName) {
        final SubInterfaceBuilder subIntfBld = new SubInterfaceBuilder();
        subIntfBld.setKey(new SubInterfaceKey((long) vlan.getValue()))
                .setIdentifier((long) vlan.getValue())
                .setL2(createSubInterfaceL2(bridgeDomainName))
                .setMatch(createMatch())
                .setEnabled(true)
                .setVlanType(vlanType)
                .setTags(createTags(vlan));
        return subIntfBld.build();
    }

    static String deriveDistinguisher(final TopologyVbridgeAugment config) {
        String def = "";
        final TunnelParameters tunnelParameters = config.getTunnelParameters();
        final Class<? extends TunnelType> tunnelType = config.getTunnelType();

        if (tunnelType.equals(TunnelTypeVxlan.class)) {
            if (tunnelParameters instanceof VxlanTunnelParameters) {
                final VxlanTunnelParameters vxlanTunnelParams = (VxlanTunnelParameters) tunnelParameters;
                final VxlanVni vni = vxlanTunnelParams.getVni();
                def = vni.getValue().toString();
                int maxLength = (def.length() < VbdBridgeDomain.MAXLEN) ? def.length() : VbdBridgeDomain.MAXLEN;
                def = def.substring(0, maxLength);
            }
        }
        return def;
    }

    static Link prepareLinkData(final NodeId newVpp, final NodeId oldVpp, final LinkId linkId,
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

    static String provideIidBridgeDomainOnVPPRest(final String bridgeDomainName) {
        return BRIDGE_DOMAIN_IID_PREFIX + bridgeDomainName;
    }

    static String provideVxlanId(final int vxlanTunnelId) {
        return TUNNEL_ID_PREFIX + vxlanTunnelId;
    }

    static void printVbridgeParams(final Topology t) {
        TopologyVbridgeAugment t2 = t.getAugmentation(TopologyVbridgeAugment.class);
        LOG.debug("Bridge Domain parameters:");
        LOG.debug("tunnelType: {}", t2.getTunnelType().getCanonicalName());
        LOG.debug("isFlood: {}", t2.isFlood());
        LOG.debug("isArpTermination: {}", t2.isArpTermination());
        LOG.debug("isForward: {}", t2.isForward());
        LOG.debug("isLearn: {}", t2.isLearn());
        LOG.debug("isUnknownUnicastFlood: {}", t2.isUnknownUnicastFlood());

        if (t2.getTunnelType().equals(TunnelTypeVxlan.class)) {
            final VxlanTunnelParameters vxlanTunnelParams = (VxlanTunnelParameters) t2.getTunnelParameters();

            if (vxlanTunnelParams == null) {
                LOG.warn("Vxlan type topology was created but vxlan tunnel parameters is null!");
                return;
            }

            final VxlanVni vni = vxlanTunnelParams.getVni();

            if (vni == null) {
                LOG.warn("Vxlan type topology was created but VNI parameter is null!");
                return;
            }

            LOG.debug("vxlan vni: {}", vni.getValue());
        } else if (t2.getTunnelType().equals(TunnelTypeVlan.class)) {
            final VlanNetworkParameters vlanNetworkParameters = (VlanNetworkParameters) t2.getTunnelParameters();
            LOG.debug("vlan-type: {} vlan-id: {}", vlanNetworkParameters.getVlanId(), vlanNetworkParameters.getVlanType());
        }
    }

    /**
     * Write {@link BridgeDomainStatusFields.BridgeDomainStatus} into OPER DS
     *
     * @param bdName name to identify bridge domain
     * @param status which will be written
     */
    static ListenableFuture<Void> updateStatus(final DataBroker dataBroker, final String bdName,
                                               final BridgeDomainStatusFields.BridgeDomainStatus status) {
        final InstanceIdentifier<BridgeDomain> vppStatusIid =
                InstanceIdentifier.builder(BridgeDomainsState.class)
                        .child(BridgeDomain.class, new BridgeDomainKey(bdName))
                        .build();
        final BridgeDomainStatusAugmentationBuilder bdStatusAugmentationBuilder =
                new BridgeDomainStatusAugmentationBuilder();
        bdStatusAugmentationBuilder.setBridgeDomainStatus(status);
        final BridgeDomain bdState = new BridgeDomainBuilder()
                .setName(bdName)
                .setKey(new BridgeDomainKey(bdName))
                .addAugmentation(BridgeDomainStatusAugmentation.class, bdStatusAugmentationBuilder.build())
                .build();
        final WriteTransaction wTx = dataBroker.newWriteOnlyTransaction();
        wTx.put(LogicalDatastoreType.OPERATIONAL, vppStatusIid, bdState);
        Futures.addCallback(wTx.submit(), new FutureCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void aVoid) {
                LOG.debug("Status updated for bridge domain {}. Current status: {}", bdName, status);
            }

            @Override
            public void onFailure(@Nonnull Throwable throwable) {
                // TODO This failure can cause problems in GBP, because it listens on status changes
                LOG.warn("Failed to update status for bridge domain {}. Current status: {}", bdName, status);
                if (throwable.getClass().equals(OptimisticLockFailedException.class)) {
                    LOG.warn("Re-updating status for bridge domain {}", bdName);
                    updateStatus(dataBroker, bdName, status);
                }
            }
        });
        return Futures.immediateFuture(null);
    }

    // Private util methods

    private static TopologyTypesVbridgeAugment createVbridgeTopologyType() {
        final TopologyTypesVbridgeAugmentBuilder bld = new TopologyTypesVbridgeAugmentBuilder();
        bld.setVbridgeTopology(new VbridgeTopologyBuilder().build());
        return bld.build();
    }

    private static VlanTagged createVlanTagged() {
        final org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev170315.match.attributes.match.type.vlan.tagged.VlanTaggedBuilder
                vlanTagBld1 = new org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev170315.match.attributes.match.type.vlan.tagged.VlanTaggedBuilder();

        final VlanTaggedBuilder vlanTagBuilder = new VlanTaggedBuilder();
        vlanTagBuilder.setVlanTagged(vlanTagBld1.setMatchExactTags(true).build());
        return vlanTagBuilder.build();
    }

    private static L2 createSubInterfaceL2(final String bridgeDomainName) {
        final RewriteBuilder rewriteBld = new RewriteBuilder();
        rewriteBld.setPopTags((short) 1);

        final BridgeBasedBuilder bridgeBld = new BridgeBasedBuilder();
        bridgeBld.setBridgeDomain(bridgeDomainName);
        bridgeBld.setBridgedVirtualInterface(false);

        final L2Builder l2Bld = new L2Builder();
        l2Bld.setRewrite(rewriteBld.build());
        l2Bld.setInterconnection(bridgeBld.build());

        return l2Bld.build();
    }

    private static Match createMatch() {
        final MatchBuilder matchBld = new MatchBuilder();
        matchBld.setMatchType(createVlanTagged());
        return matchBld.build();
    }

    private static Tags createTags(VlanId vlan) {
        return new TagsBuilder().setTag(Collections.singletonList(new TagBuilder().setIndex(VbdBridgeDomain.VLAN_TAG_INDEX_ZERO)
                .setDot1qTag(new Dot1qTagBuilder().setTagType(SVlan.class)
                        .setVlanId(
                                new Dot1qTag.VlanId(
                                        new Dot1qVlanId(vlan.getValue())))
                        .build())
                .build())).build();
    }
}
