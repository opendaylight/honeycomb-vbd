/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vbd.impl.transaction;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.CheckedFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
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

    public static final byte RETRY_COUNT = 3;
    private static final Logger LOG = LoggerFactory.getLogger(VbdNetconfTransaction.class);

    /**
     * Write data to remote device. Transaction is restarted if failed
     *
     * @param mountpoint   to access remote device
     * @param iid          data identifier
     * @param data         to write
     * @param retryCounter number of attempts
     * @param <T>          generic data type. Has to be child of {@link DataObject}
     * @return true if transaction is successful, false otherwise
     */
    public synchronized static <T extends DataObject> boolean write(final DataBroker mountpoint,
                                                                    final InstanceIdentifier<T> iid,
                                                                    final T data,
                                                                    byte retryCounter) {
        LOG.trace("Netconf WRITE transaction started. RetryCounter: {}", retryCounter);
        Preconditions.checkNotNull(mountpoint);
        final ReadWriteTransaction rwTx = mountpoint.newReadWriteTransaction();
        try {
            rwTx.put(LogicalDatastoreType.CONFIGURATION, iid, data, true);
            final CheckedFuture<Void, TransactionCommitFailedException> futureTask = rwTx.submit();
            futureTask.get();
            LOG.trace("Netconf WRITE transaction done for {}", iid);
            return true;
        } catch (Exception e) {
            // Retry
            if (retryCounter > 0) {
                LOG.warn("Netconf WRITE transaction failed to {}. Restarting transaction ... ", e.getMessage());
                return write(mountpoint, iid, data, --retryCounter);
            } else {
                LOG.warn("Netconf WRITE transaction unsuccessful. Maximal number of attempts reached. Trace: {}", e);
                return false;
            }
        }
    }

    /**
     * Read data from remote device. Transaction is restarted if failed.
     *
     * @param mountpoint    to access remote device
     * @param datastoreType {@link LogicalDatastoreType}
     * @param iid           data identifier
     * @param retryCounter  number of attempts
     * @param <T>           generic data type. Has to be child of {@link DataObject}
     * @return optional data object if successful, {@link Optional#absent()} if failed
     */
    public synchronized static <T extends DataObject> Optional<T> read(final DataBroker mountpoint,
                                                                       final LogicalDatastoreType datastoreType,
                                                                       final InstanceIdentifier<T> iid,
                                                                       byte retryCounter) {
        LOG.trace("Netconf READ transaction started. RetryCounter: {}", retryCounter);
        Preconditions.checkNotNull(mountpoint);
        final ReadOnlyTransaction rTx = mountpoint.newReadOnlyTransaction();
        Optional<T> data;
        try {
            final CheckedFuture<Optional<T>, ReadFailedException> futureData =
                    rTx.read(datastoreType, iid);
            data = futureData.get();
            LOG.trace("Netconf READ transaction done. Data present: {}", data.isPresent());
            return data;
        } catch (Exception e) {
            // Retry
            if (retryCounter > 0) {
                LOG.warn("Netconf READ transaction failed to {}. Restarting transaction ... ", e.getMessage());
                rTx.close();
                return read(mountpoint, datastoreType, iid, --retryCounter);
            } else {
                LOG.warn("Netconf READ transaction unsuccessful. Maximal number of attempts reached. Trace: {}", e);
                return Optional.absent();
            }
        }
    }

    /**
     * Remove data from remote device. Data are read and deleted only if exist. Transaction is restarted if failed.
     *
     * @param mountpoint   to access remote device
     * @param iid          data identifier
     * @param retryCounter number of attempts
     * @param <T>          generic data type. Has to be child of {@link DataObject}
     * @return true if transaction is successful, false otherwise
     */
    public synchronized static <T extends DataObject> boolean deleteIfExists(final DataBroker mountpoint,
                                                                             final InstanceIdentifier<T> iid,
                                                                             byte retryCounter) {
        LOG.trace("Netconf DELETE transaction started. Data will be read at first. RetryCounter: {}", retryCounter);
        Preconditions.checkNotNull(mountpoint);
        final Optional<T> optionalObject = read(mountpoint, LogicalDatastoreType.CONFIGURATION, iid, RETRY_COUNT);
        if (!optionalObject.isPresent()) {
            LOG.warn("Netconf DELETE transaction aborted. Data to remove are not present or cannot be read. Iid: {}",
                    iid);
            // Return true, this state is not considered as an error
            return true;
        }
        final ReadWriteTransaction rwTx = mountpoint.newReadWriteTransaction();
        try {
            rwTx.delete(LogicalDatastoreType.CONFIGURATION, iid);
            final CheckedFuture<Void, TransactionCommitFailedException> futureTask = rwTx.submit();
            futureTask.get();
            LOG.trace("Netconf DELETE transaction done for {}", iid);
            return true;
        } catch (Exception e) {
            // Retry
            if (retryCounter > 0) {
                LOG.warn("Netconf DELETE transaction failed to {}. Restarting transaction ... ", e.getMessage());
                return deleteIfExists(mountpoint, iid, --retryCounter);
            } else {
                LOG.warn("Netconf DELETE transaction unsuccessful. Maximal number of attempts reached. Trace: {}", e);
                return false;
            }
        }
    }
}
