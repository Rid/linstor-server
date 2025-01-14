package com.linbit.linstor.numberpool;

import com.linbit.ImplementationError;
import com.linbit.ValueInUseException;
import com.linbit.linstor.annotation.SystemContext;
import com.linbit.linstor.core.CoreModule;
import com.linbit.linstor.core.objects.NetInterface;
import com.linbit.linstor.core.objects.Node;
import com.linbit.linstor.core.objects.Node.Type;
import com.linbit.linstor.core.objects.Resource;
import com.linbit.linstor.core.objects.Snapshot;
import com.linbit.linstor.logging.ErrorReporter;
import com.linbit.linstor.security.AccessContext;
import com.linbit.linstor.security.AccessDeniedException;
import com.linbit.linstor.storage.interfaces.categories.resource.AbsRscLayerObject;
import com.linbit.linstor.systemstarter.StartupInitializer;

import static com.linbit.linstor.numberpool.NumberPoolModule.LAYER_RSC_ID_POOL;
import static com.linbit.linstor.numberpool.NumberPoolModule.MINOR_NUMBER_POOL;
import static com.linbit.linstor.numberpool.NumberPoolModule.SNAPSHOPT_SHIPPING_PORT_POOL;
import static com.linbit.linstor.numberpool.NumberPoolModule.SPECIAL_SATELLTE_PORT_POOL;
import static com.linbit.linstor.numberpool.NumberPoolModule.TCP_PORT_POOL;

import javax.inject.Inject;
import javax.inject.Named;

import java.util.Iterator;

public class DbNumberPoolInitializer implements StartupInitializer
{
    private final ErrorReporter errorReporter;
    private final AccessContext initCtx;
    private final DynamicNumberPool minorNrPool;
    private final DynamicNumberPool tcpPortPool;
    private final DynamicNumberPool ofTargetPortPool;
    private final DynamicNumberPool layerRscIdPool;
    private final CoreModule.ResourceDefinitionMap rscDfnMap;
    private final CoreModule.NodesMap nodesMap;
    private final DynamicNumberPool snapShipPortPool;

    @Inject
    public DbNumberPoolInitializer(
        ErrorReporter errorReporterRef,
        @SystemContext AccessContext initCtxRef,
        @Named(MINOR_NUMBER_POOL) DynamicNumberPool minorNrPoolRef,
        @Named(TCP_PORT_POOL) DynamicNumberPool tcpPortPoolRef,
        @Named(SPECIAL_SATELLTE_PORT_POOL) DynamicNumberPool ofTargetPortPoolRef,
        @Named(LAYER_RSC_ID_POOL) DynamicNumberPool layerRscIdPoolRef,
        @Named(SNAPSHOPT_SHIPPING_PORT_POOL) DynamicNumberPool snapShipPortPoolRef,
        CoreModule.ResourceDefinitionMap rscDfnMapRef,
        CoreModule.NodesMap nodesMapRef
    )
    {
        errorReporter = errorReporterRef;
        initCtx = initCtxRef;
        minorNrPool = minorNrPoolRef;
        tcpPortPool = tcpPortPoolRef;
        ofTargetPortPool = ofTargetPortPoolRef;
        layerRscIdPool = layerRscIdPoolRef;
        snapShipPortPool = snapShipPortPoolRef;
        rscDfnMap = rscDfnMapRef;
        nodesMap = nodesMapRef;
    }

    @Override
    public void initialize()
    {
        initializeMinorNrPool();
        initializeTcpPortPool();
        initializeOpenflexTargetPortPool();
        initializeLayerRscIdPool();
        initializeSnapShipPortPool();
    }

    private void initializeMinorNrPool()
    {
        minorNrPool.reloadRange();
    }

    private void initializeTcpPortPool()
    {
        tcpPortPool.reloadRange();
    }

    private void initializeOpenflexTargetPortPool()
    {
        ofTargetPortPool.reloadRange();
        try
        {
            for (Node curNode : nodesMap.values())
            {
                Type nodeType = curNode.getNodeType(initCtx);
                if (Node.Type.OPENFLEX_TARGET.equals(nodeType) || Node.Type.REMOTE_SPDK.equals(nodeType))
                {
                    try
                    {
                        Iterator<NetInterface> netIfIt = curNode.iterateNetInterfaces(initCtx);
                        int netIfCount = 0;
                        while (netIfIt.hasNext())
                        {
                            if (++netIfCount > 1)
                            {
                                throw new ImplementationError(
                                    "Openflex target node has more than one network interface!"
                                );
                            }
                            NetInterface netIf = netIfIt.next();
                            ofTargetPortPool.allocate(netIf.getStltConnPort(initCtx).value);
                        }
                        if (netIfCount == 0)
                        {
                            throw new ImplementationError(
                                "Openflex target node has no network interface!"
                            );
                        }
                    }
                    catch (ValueInUseException exc)
                    {
                        errorReporter.logError(
                            "Skipping initial allocation in pool: " + exc.getMessage()
                        );
                    }
                }
            }
        }
        catch (AccessDeniedException accExc)
        {
            throw new ImplementationError(
                "An " + accExc.getClass().getSimpleName() + " exception was generated " +
                    "during number allocation cache initialization",
                accExc
            );
        }
    }

    private void initializeLayerRscIdPool()
    {
        layerRscIdPool.reloadRange();

        try
        {
            for (Node curNode : nodesMap.values())
            {
                Iterator<Resource> iterateResources = curNode.iterateResources(initCtx);
                while (iterateResources.hasNext())
                {
                    Resource rsc = iterateResources.next();
                    AbsRscLayerObject<?> rscLayerData = rsc.getLayerData(initCtx);

                    allocate(rscLayerData);
                }
                Iterator<Snapshot> iterateSnapshots = curNode.iterateSnapshots(initCtx);
                while (iterateSnapshots.hasNext())
                {
                    Snapshot snapshot = iterateSnapshots.next();
                    AbsRscLayerObject<?> rscLayerData = snapshot.getLayerData(initCtx);

                    allocate(rscLayerData);
                }

            }
        }
        catch (AccessDeniedException | ValueInUseException exc)
        {
            throw new ImplementationError(
                "An " + exc.getClass().getSimpleName() + " exception was generated " +
                    "during number allocation cache initialization",
                    exc
                );
        }
    }

    private void allocate(AbsRscLayerObject<?> rscLayerDataRef) throws ValueInUseException
    {
        layerRscIdPool.allocate(rscLayerDataRef.getRscLayerId());
        for (AbsRscLayerObject<?> childRscLayerData : rscLayerDataRef.getChildren())
        {
            allocate(childRscLayerData);
        }
    }

    private void initializeSnapShipPortPool()
    {
        snapShipPortPool.reloadRange();
    }
}
