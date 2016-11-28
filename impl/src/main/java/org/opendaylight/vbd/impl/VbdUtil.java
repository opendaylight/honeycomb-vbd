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
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev161214.VppState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev161214.vpp.state.BridgeDomains;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev161214.vpp.state.bridge.domains.BridgeDomain;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev161214.vpp.state.bridge.domains.BridgeDomainBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev161214.vpp.state.bridge.domains.BridgeDomainKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.status.rev161005.BridgeDomainStatusAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.status.rev161005.BridgeDomainStatusAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbridge.status.rev161005.BridgeDomainStatusFields;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class VbdUtil {

    private static final Logger LOG = LoggerFactory.getLogger(VbdUtil.class);
    private static final String TUNNEL_ID_PREFIX = "vxlan_tunnel";


    private VbdUtil() {
        throw new UnsupportedOperationException("Can't instantiate util class");
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

    static String provideVxlanId(final int vxlanTunnelId) {
        return TUNNEL_ID_PREFIX + vxlanTunnelId;
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
                InstanceIdentifier.builder(VppState.class)
                        .child(BridgeDomains.class)
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
}
