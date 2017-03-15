package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbd.impl.rev160202;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.MountPointService;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.mdsal.singleton.common.api.ClusterSingletonService;
import org.opendaylight.mdsal.singleton.common.api.ClusterSingletonServiceProvider;
import org.opendaylight.mdsal.singleton.common.api.ClusterSingletonServiceRegistration;
import org.opendaylight.mdsal.singleton.common.api.ServiceGroupIdentifier;
import org.opendaylight.vbd.impl.VirtualBridgeDomainManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VbdInstance implements AutoCloseable, ClusterSingletonService {

    private static final ServiceGroupIdentifier IDENTIFIER =
            ServiceGroupIdentifier.create("vbd-service-group-identifier");
    private final Logger LOG = LoggerFactory.getLogger(VbdInstance.class);
    private final DataBroker dataBroker;
    private final ClusterSingletonServiceProvider clusterProvider;
    private final MountPointService mountService;
    private ClusterSingletonServiceRegistration singletonServiceRegistration;
    private VirtualBridgeDomainManager vbdManager;
    private String virtualDomainInterfaces;

    public VbdInstance(final DataBroker dataBroker,
                       final BindingAwareBroker bindingAwareBroker,
                       final ClusterSingletonServiceProvider clusterProvider,
                       final String virtualDomainInterfaces) {
        final BindingAwareBroker.ProviderContext session = Preconditions.checkNotNull(bindingAwareBroker)
                .registerProvider(new VbdProvider());
        this.dataBroker = Preconditions.checkNotNull(dataBroker);
        this.mountService = Preconditions.checkNotNull(session.getSALService(MountPointService.class));
        this.clusterProvider = Preconditions.checkNotNull(clusterProvider);
        this.virtualDomainInterfaces = virtualDomainInterfaces;
    }

    public void initialize() {
        LOG.info("Clustering session initiated for {}", this.getClass().getSimpleName());
        singletonServiceRegistration = clusterProvider.registerClusterSingletonService(this);
    }

    @Override
    public ServiceGroupIdentifier getIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public void instantiateServiceInstance() {
        LOG.info("Instantiating {}", this.getClass().getSimpleName());
        vbdManager = VirtualBridgeDomainManager.create(dataBroker, mountService, virtualDomainInterfaces);
    }

    @Override
    public ListenableFuture<Void> closeServiceInstance() {
        vbdManager.close();
        return Futures.immediateFuture(null);
    }

    @Override
    public void close() throws Exception {
        LOG.info("Clustering provider closed for {}", this.getClass().getSimpleName());
        if (singletonServiceRegistration != null) {
            try {
                singletonServiceRegistration.close();
            } catch (Exception e) {
                LOG.warn("{} closed unexpectedly", this.getClass().getSimpleName(), e);
            }
            singletonServiceRegistration = null;
        }
    }
}
