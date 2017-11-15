package com.linbit.drbdmanage.core;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.linbit.ImplementationError;
import com.linbit.InvalidNameException;
import com.linbit.SatelliteTransactionMgr;
import com.linbit.ValueOutOfRangeException;
import com.linbit.drbdmanage.MinorNumber;
import com.linbit.drbdmanage.Node;
import com.linbit.drbdmanage.Node.NodeFlag;
import com.linbit.drbdmanage.Node.NodeType;
import com.linbit.drbdmanage.NodeData;
import com.linbit.drbdmanage.NodeId;
import com.linbit.drbdmanage.NodeName;
import com.linbit.drbdmanage.Resource;
import com.linbit.drbdmanage.Resource.RscFlags;
import com.linbit.drbdmanage.ResourceData;
import com.linbit.drbdmanage.ResourceDefinition;
import com.linbit.drbdmanage.ResourceDefinition.RscDfnFlags;
import com.linbit.drbdmanage.ResourceDefinitionData;
import com.linbit.drbdmanage.ResourceName;
import com.linbit.drbdmanage.StorPool;
import com.linbit.drbdmanage.StorPoolName;
import com.linbit.drbdmanage.TcpPortNumber;
import com.linbit.drbdmanage.Volume.VlmFlags;
import com.linbit.drbdmanage.VolumeData;
import com.linbit.drbdmanage.VolumeDefinition;
import com.linbit.drbdmanage.VolumeDefinition.VlmDfnFlags;
import com.linbit.drbdmanage.api.raw.ResourceRawData;
import com.linbit.drbdmanage.api.raw.ResourceRawData.OtherRscRawData;
import com.linbit.drbdmanage.api.raw.ResourceRawData.VolumeDfnRawData;
import com.linbit.drbdmanage.api.raw.ResourceRawData.VolumeRawData;
import com.linbit.drbdmanage.VolumeDefinitionData;
import com.linbit.drbdmanage.VolumeNumber;
import com.linbit.drbdmanage.security.AccessContext;
import com.linbit.drbdmanage.security.AccessDeniedException;

public class StltRscApiCallHandler
{
    private final Satellite satellite;
    private final AccessContext apiCtx;

    public StltRscApiCallHandler(Satellite satelliteRef, AccessContext apiCtxRef)
    {
        satellite = satelliteRef;
        apiCtx = apiCtxRef;
    }

    public void deployResource(ResourceRawData rscRawData)
        throws DivergentDataException
    {
        SatelliteTransactionMgr transMgr = new SatelliteTransactionMgr();
        ResourceName rscName;

        ResourceDefinitionData rscDfnToRegister = null;
        List<NodeData> nodesToRegister = new ArrayList<>();

        try
        {
            rscName = new ResourceName(rscRawData.getRscName());
            TcpPortNumber port = new TcpPortNumber(rscRawData.getRscDfnPort());
            RscDfnFlags[] rscDfnFlags = RscDfnFlags.restoreFlags(rscRawData.getRscDfnFlags());

            ResourceDefinitionData rscDfn = (ResourceDefinitionData) satellite.rscDfnMap.get(rscName);

            Resource localRsc = null;
            Set<Resource> otherRscs = new HashSet<>();
            if (rscDfn == null)
            {
                rscDfn = ResourceDefinitionData.getInstanceSatellite(
                    apiCtx,
                    rscRawData.getRscDfnUuid(),
                    rscName,
                    port,
                    rscDfnFlags,
                    transMgr
                );

                checkUuid(rscDfn, rscRawData);
                rscDfnToRegister = rscDfn;
            }
            rscDfn.setConnection(transMgr);
            rscDfn.setPort(apiCtx, port);
            Map<String, String> rscDfnProps = rscDfn.getProps(apiCtx).map();
            rscDfnProps.clear();
            rscDfnProps.putAll(rscRawData.getRscDfnProps());
            rscDfn.getFlags().resetFlagsTo(apiCtx, rscDfnFlags);

            Iterator<Resource> rscIterator = rscDfn.iterateResource(apiCtx);
            if (!rscIterator.hasNext())
            {
                // our rscDfn is empty
                // that means, just create everything we need

                for (VolumeDfnRawData vlmDfnRaw : rscRawData.getVlmDfns())
                {
                    VolumeNumber vlmNr = new VolumeNumber(vlmDfnRaw.getVlmNr());
                    VolumeDefinitionData vlmDfn = VolumeDefinitionData.getInstanceSatellite(
                        apiCtx,
                        vlmDfnRaw.getVlmDfnUuid(),
                        rscDfn,
                        vlmNr,
                        vlmDfnRaw.getVlmSize(),
                        new MinorNumber(vlmDfnRaw.getVlmMinor()),
                        VlmDfnFlags.restoreFlags(vlmDfnRaw.getFlags()),
                        transMgr
                    );
                    checkUuid(vlmDfn, vlmDfnRaw, rscName.displayValue);
                    Map<String, String> vlmDfnPropsMap = vlmDfn.getProps(apiCtx).map();
                    vlmDfnPropsMap.clear();
                    vlmDfnPropsMap.putAll(vlmDfnRaw.getVlmDfnProps());
                }

                NodeData localNode = satellite.getLocalNode();

                localRsc = createRsc(
                    rscRawData.getLocalRscUuid(),
                    localNode,
                    rscDfn,
                    new NodeId(rscRawData.getLocalRscNodeId()),
                    RscFlags.restoreFlags(rscRawData.getLocalRscFlags()),
                    rscRawData.getLocalRscProps(),
                    rscRawData.getLocalVlms(),
                    transMgr,
                    false
                );

                for (OtherRscRawData otherRscRaw : rscRawData.getOtherRscList())
                {
                    NodeData remoteNode = NodeData.getInstanceSatellite(
                        apiCtx,
                        otherRscRaw.getNodeUuid(),
                        new NodeName(otherRscRaw.getNodeName()),
                        NodeType.getByValue(otherRscRaw.getNodeType()),
                        NodeFlag.restoreFlags(otherRscRaw.getNodeFlags()),
                        transMgr
                    );
                    checkUuid(remoteNode, otherRscRaw);

                    otherRscs.add(
                        createRsc(
                            otherRscRaw.getRscUuid(),
                            remoteNode,
                            rscDfn,
                            new NodeId(otherRscRaw.getRscNodeId()),
                            RscFlags.restoreFlags(otherRscRaw.getRscFlags()),
                            otherRscRaw.getRscProps(),
                            otherRscRaw.getVlms(),
                            transMgr,
                            true
                        )
                    );
                }
            }
            else
            {
                // iterator contains at least one resource.

                List<Resource> removedList = new ArrayList<>();
                List<Resource> newResources = new ArrayList<>();
                List<Resource> modifiedResources = new ArrayList<>();
                List<Node> nodesToRemove = new ArrayList<>();


                // first we split the existing resources into our local resource and a list of others
                while (rscIterator.hasNext())
                {
                    Resource rsc = rscIterator.next();
                    if (rsc.getUuid().equals(rscRawData.getLocalRscUuid()))
                    {
                        localRsc = rsc;
                    }
                    else
                    {
                        removedList.add(rsc);
                    }
                    // TODO: resources will still remain in the rscDfn. maybe this will get a problem
                }
                // now we should have found our localRsc, and added all all other resources in removedList
                // we will delete them from the list as we find them with matching uuid and node names

                if (localRsc == null)
                {
                    throw new DivergentUuidsException(
                        String.format(
                            "The local resource with the UUID '%s' was not found in the stored " +
                                "resource definition '%s'.",
                            rscRawData.getLocalRscUuid().toString(),
                            rscName
                        )
                    );
                }
                for (OtherRscRawData otherRsc : rscRawData.getOtherRscList())
                {
                    Resource remoteRsc = null;
                    for (Resource removed : removedList)
                    {
                        if (otherRsc.getRscUuid().equals(removed.getUuid()))
                        {
                            if (!otherRsc.getNodeName().equals(
                                removed.getAssignedNode().getName().displayValue)
                            )
                            {
                                throw new DivergentDataException(
                                    "The resource with UUID '%s' was deployed on node '%s' but is now " +
                                        "on node '%s' (this should have cause a delete and re-deploy of that resource)."
                                );
                            }
                            if (!otherRsc.getNodeUuid().equals(
                                removed.getAssignedNode().getUuid())
                            )
                            {
                                throw new DivergentUuidsException(
                                    "Node",
                                    removed.getAssignedNode().getName().displayValue,
                                    otherRsc.getNodeName(),
                                    removed.getAssignedNode().getUuid(),
                                    otherRsc.getNodeUuid()
                                );
                            }

                            remoteRsc = removed;
                            break;
                        }
                    }
                    if (remoteRsc == null)
                    {
                        // controller sent us a resource that we don't know
                        // create its node
                        NodeName nodeName = new NodeName(otherRsc.getNodeName());
                        NodeData remoteNode = (NodeData) satellite.nodesMap.get(nodeName);
                        if (remoteNode == null)
                        {
                            remoteNode = NodeData.getInstanceSatellite(
                                apiCtx,
                                otherRsc.getNodeUuid(),
                                nodeName,
                                NodeType.getByValue(otherRsc.getNodeType()),
                                NodeFlag.restoreFlags(otherRsc.getNodeFlags()),
                                transMgr
                            );
                            nodesToRegister.add(remoteNode);
                        }
                        else
                        {
                            checkUuid(remoteNode, otherRsc);
                        }
                        Map<String, String> map = remoteNode.getProps(apiCtx).map();
                        map.clear();
                        map.putAll(otherRsc.getNodeProps());

                        // create resource
                        remoteRsc = createRsc(
                            otherRsc.getRscUuid(),
                            remoteNode,
                            rscDfn,
                            new NodeId(otherRsc.getRscNodeId()),
                            RscFlags.restoreFlags(otherRsc.getRscFlags()),
                            otherRsc.getRscProps(),
                            otherRsc.getVlms(),
                            transMgr,
                            true
                        );

                        // everything ok, mark the resource as new
                        newResources.add(remoteRsc);
                    }
                    else
                    {
                        // we found the resource by the uuid the controller sent us
                        Node remoteNode = remoteRsc.getAssignedNode();
                        // check if the node uuids also match
                        checkUuid(remoteNode, otherRsc);

                        // update node props
                        remoteNode.setConnection(transMgr);
                        Map<String, String> remoteNodeProps = remoteNode.getProps(apiCtx).map();
                        remoteNodeProps.clear();
                        remoteNodeProps.putAll(otherRsc.getNodeProps());

                        // update matching resource props
                        remoteRsc.setConnection(transMgr);
                        Map<String, String> remoteRscProps = remoteRsc.getProps(apiCtx).map();
                        remoteRscProps.clear();
                        remoteRscProps.putAll(otherRsc.getRscProps());

                        // TODO: volumes
                        List<VolumeRawData> otherRscVlms = otherRsc.getVlms();

                        // everything ok, mark the resource to be kept
                        removedList.remove(remoteRsc);
                        modifiedResources.add(remoteRsc);
                    }
                    otherRscs.add(remoteRsc);
                }
                // all resources have been created or updated

                // cleanup

                // first, iterate over all resources marked for deletion and unlink them from rscDfn and node
                for (Resource rsc : removedList)
                {
                    // TODO: start undeploying this resource on drbd level
                    // TODO: when undeploy is done, check the node if it has resources left. if not, remove node
                    rsc.markDeleted(apiCtx);
                }
            }

            if (rscDfnToRegister != null)
            {
                satellite.rscDfnMap.put(rscName, rscDfnToRegister);
            }
            for (Node node : nodesToRegister)
            {
                satellite.nodesMap.put(node.getName(), node);
            }
            transMgr.commit();

            // TODO: deploy to drbd
        }
        catch (InvalidNameException | ValueOutOfRangeException | NullPointerException invalidDataExc)
        {
            satellite.getErrorReporter().reportError(
                new ImplementationError(
                    "Controller sent invalid Data for resource deployment",
                    invalidDataExc
                )
            );
        }
        catch (AccessDeniedException accDeniedExc)
        {
            satellite.getErrorReporter().reportError(
                new ImplementationError(
                    "Satellite's apiContext has not enough privileges to deploy resource",
                    accDeniedExc
                )
            );
        }
        catch (SQLException sqlExc)
        {
            satellite.getErrorReporter().reportError(
                new ImplementationError(
                    "Satellite should not throw this exception.",
                    sqlExc
                )
            );
        }
    }

    private ResourceData createRsc(
        UUID rscUuid,
        NodeData node,
        ResourceDefinitionData rscDfn,
        NodeId nodeId,
        RscFlags[] flags,
        Map<String, String> rscProps,
        List<VolumeRawData> vlms,
        SatelliteTransactionMgr transMgr,
        boolean remoteRsc
    )
        throws AccessDeniedException, ValueOutOfRangeException, InvalidNameException, DivergentDataException
    {
        ResourceData rsc = ResourceData.getInstanceSatellite(
            apiCtx,
            rscUuid,
            node,
            rscDfn,
            nodeId,
            flags,
            transMgr
        );

        checkUuid(
            rsc.getUuid(),
            rscUuid,
            "Resource",
            rsc.toString(),
            "Node: '" + node.getName().displayValue + "', RscName: '" + rscDfn.getName().displayValue + "'"
        );
        rsc.setConnection(transMgr);

        Map<String, String> map = rsc.getProps(apiCtx).map();
        map.clear();
        map.putAll(rscProps);

        for (VolumeRawData vlmRaw : vlms)
        {
            StorPool storPool = node.getStorPool(
                apiCtx,
                new StorPoolName(vlmRaw.getStorPoolName())
            );
            if (storPool == null)
            {
                if (remoteRsc)
                {
                    storPool = Satellite.DUMMY_REMOTE_STOR_POOL;
                }
                else
                {
                    throw new DivergentDataException("Unknown StorPool: '" + vlmRaw.getStorPoolName() + "'");
                }
            }
            if (!remoteRsc && !storPool.getUuid().equals(vlmRaw.getStorPoolUuid()))
            {
                throw new DivergentUuidsException(
                    "StorPool",
                    storPool.toString(),
                    vlmRaw.getStorPoolName(),
                    storPool.getUuid(),
                    vlmRaw.getStorPoolUuid()
                );
            }

            VolumeDefinition vlmDfn = rscDfn.getVolumeDfn(apiCtx, new VolumeNumber(vlmRaw.getVlmNr()));

            VolumeData vlm = VolumeData.getInstanceSatellite(
                apiCtx,
                vlmRaw.getVlmUuid(),
                rsc,
                vlmDfn,
                storPool,
                vlmRaw.getBlockDevice(),
                vlmRaw.getMetaDisk(),
                VlmFlags.restoreFlags(vlmRaw.getVlmFlags()),
                transMgr
            );
            checkUuid(
                vlm,
                vlmRaw,
                node.getName().displayValue,
                rscDfn.getName().displayValue
            );
        }

        return rsc;
    }

    private void checkUuid(Node node, OtherRscRawData otherRsc)
        throws DivergentUuidsException
    {
        checkUuid(
            node.getUuid(),
            otherRsc.getNodeUuid(),
            "Node",
            node.getName().displayValue,
            otherRsc.getNodeName()
        );
    }

    private void checkUuid(ResourceDefinition rscDfn, ResourceRawData rscRawData)
        throws DivergentUuidsException
    {
        checkUuid(
            rscDfn.getUuid(),
            rscRawData.getRscDfnUuid(),
            "ResourceDefinition",
            rscDfn.getName().displayValue,
            rscRawData.getRscName()
        );
    }

    private void checkUuid(Resource rsc, OtherRscRawData otherRsc, String otherRscName)
        throws DivergentUuidsException
    {
        checkUuid(
            rsc.getUuid(),
            otherRsc.getRscUuid(),
            "Resource",
            String.format("Node: '%s', Rsc: '%s'",
                rsc.getAssignedNode().getName().displayValue,
                rsc.getDefinition().getName().displayValue
            ),
            String.format("Node: '%s', Rsc: '%s'",
                otherRsc.getNodeName(),
                otherRscName
            )
        );
    }

    private void checkUuid(
        VolumeDefinitionData vlmDfn,
        VolumeDfnRawData vlmDfnRaw,
        String remoteRscName
    )
        throws DivergentUuidsException
    {
        checkUuid(
            vlmDfn.getUuid(),
            vlmDfnRaw.getVlmDfnUuid(),
            "VolumeDefinition",
            vlmDfn.toString(),
            String.format(
                "Rsc: '%s', VlmNr: '%d'",
                remoteRscName,
                vlmDfnRaw.getVlmNr()
            )
        );
    }

    private void checkUuid(
        VolumeData vlm,
        VolumeRawData vlmRaw,
        String remoteNodeName,
        String remoteRscName
    )
        throws DivergentUuidsException
    {
        checkUuid(
            vlm.getUuid(),
            vlmRaw.getVlmUuid(),
            "Volume",
            vlm.toString(),
            String.format(
                "Node: '%s', Rsc: '%s', VlmNr: '%d'",
                remoteNodeName,
                remoteRscName,
                vlmRaw.getVlmNr()
            )
        );
    }

    private void checkUuid(UUID localUuid, UUID remoteUuid, String type, String localName, String remoteName)
        throws DivergentUuidsException
    {
        if (!localUuid.equals(remoteUuid))
        {
            throw new DivergentUuidsException(
                type,
                localName,
                remoteName,
                localUuid,
                remoteUuid
            );
        }
    }
}
