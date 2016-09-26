package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vbd.impl.rev160202;

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

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

public class VbdInstance implements AutoCloseable, ClusterSingletonService {

    public static final Logger LOG = LoggerFactory.getLogger(VbdInstance.class);
    private static final ServiceGroupIdentifier IDENTIFIER =
            ServiceGroupIdentifier.create("vbd-service-group-identifier");
    private DataBroker dataBroker;
    private ClusterSingletonServiceProvider clusterProvider;
    private ClusterSingletonServiceRegistration singletonServiceRegistration;
    private MountPointService mountService;
    private VirtualBridgeDomainManager vbdManager;

    public VbdInstance(BindingAwareBroker broker, ClusterSingletonServiceProvider clusterProvider) {
        BindingAwareBroker.ProviderContext session = Preconditions.checkNotNull(broker)
                .registerProvider(new VbdProvider());
        this.dataBroker = session.getSALService(DataBroker.class);
        this.mountService = session.getSALService(MountPointService.class);
        this.clusterProvider = Preconditions.checkNotNull(clusterProvider);
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
        vbdManager = VirtualBridgeDomainManager.create(dataBroker, mountService);
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
