/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vbd.impl.transaction;

import java.util.concurrent.ExecutionException;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.CheckedFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides basic CRD functionality using mountpoint and perform all necessary operations to ensure successful
 * transaction. Only one transaction is permitted at the same time.
 */
public class VbdNetconfTransaction {

    public static final byte RETRY_COUNT = 5;
    private static final Logger LOG = LoggerFactory.getLogger(VbdNetconfTransaction.class);

    public synchronized static <T extends DataObject> boolean write(final DataBroker mountpoint,
                                                                    final InstanceIdentifier<T> iid,
                                                                    final T data,
                                                                    byte retryCounter) {
        Preconditions.checkNotNull(mountpoint);
        final ReadWriteTransaction rwTx = mountpoint.newReadWriteTransaction();
        try {
            rwTx.put(LogicalDatastoreType.CONFIGURATION, iid, data, true);
            final CheckedFuture<Void, TransactionCommitFailedException> futureTask = rwTx.submit();
            futureTask.get();
            return true;
        } catch (IllegalStateException e) {
            // Retry
            if (retryCounter > 0) {
                LOG.warn("Assuming that netconf write-transaction failed, restarting ...", e.getMessage());
                rwTx.cancel();
                return write(mountpoint, iid, data, --retryCounter);
            } else {
                LOG.warn("Netconf write-transaction failed. Maximal number of attempts reached", e.getMessage());
                rwTx.cancel();
                return false;
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception while writing data ...", e.getMessage());
            rwTx.cancel();
            return false;
        }
    }

    public synchronized static <T extends DataObject> Optional<T> read(final DataBroker mountpoint,
                                                                       final LogicalDatastoreType datastoreType,
                                                                       final InstanceIdentifier<T> iid,
                                                                       byte retryCounter) {
        Preconditions.checkNotNull(mountpoint);
        final ReadWriteTransaction rwTx = mountpoint.newReadWriteTransaction();
        try {
            final CheckedFuture<Optional<T>, ReadFailedException> futureData =
                    rwTx.read(datastoreType, iid);
            return futureData.get();
        } catch (IllegalStateException e) {
            // Retry
            if (retryCounter > 0) {
                LOG.warn("Assuming that netconf read-transaction failed, restarting ...", e.getMessage());
                rwTx.cancel();
                return read(mountpoint, datastoreType, iid, --retryCounter);
            } else {
                LOG.warn("Netconf read-transaction failed. Maximal number of attempts reached", e.getMessage());
                rwTx.cancel();
                return Optional.absent();
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception while reading data ...", e.getMessage());
            rwTx.cancel();
            return Optional.absent();
        }
    }

    public synchronized static <T extends DataObject> boolean delete(final DataBroker mountpoint,
                                                                     final InstanceIdentifier<T> iid,
                                                                     byte retryCounter) {
        Preconditions.checkNotNull(mountpoint);
        final ReadWriteTransaction rwTx = mountpoint.newReadWriteTransaction();
        try {
            rwTx.delete(LogicalDatastoreType.CONFIGURATION, iid);
            final CheckedFuture<Void, TransactionCommitFailedException> futureTask = rwTx.submit();
            futureTask.get();
            return true;
        } catch (IllegalStateException e) {
            // Retry
            if (retryCounter > 0) {
                LOG.warn("Assuming that netconf delete-transaction failed, restarting ...", e.getMessage());
                rwTx.cancel();
                return delete(mountpoint, iid, --retryCounter);
            } else {
                LOG.warn("Netconf delete-transaction failed. Maximal number of attempts reached", e.getMessage());
                rwTx.cancel();
                return false;
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception while removing data ...", e.getMessage());
            rwTx.cancel();
            return false;
        }
    }
}
