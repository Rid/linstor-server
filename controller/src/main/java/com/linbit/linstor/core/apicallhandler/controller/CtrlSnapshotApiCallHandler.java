package com.linbit.linstor.core.apicallhandler.controller;

import com.linbit.linstor.LinstorParsingUtils;
import com.linbit.linstor.annotation.PeerContext;
import com.linbit.linstor.api.ApiConsts;
import com.linbit.linstor.core.apicallhandler.response.ApiOperation;
import com.linbit.linstor.core.apicallhandler.response.ResponseContext;
import com.linbit.linstor.core.apis.SnapshotDefinitionListItemApi;
import com.linbit.linstor.core.identifier.NodeName;
import com.linbit.linstor.core.identifier.ResourceName;
import com.linbit.linstor.core.identifier.SnapshotName;
import com.linbit.linstor.core.identifier.VolumeNumber;
import com.linbit.linstor.core.objects.ResourceDefinition;
import com.linbit.linstor.core.objects.Snapshot;
import com.linbit.linstor.core.objects.SnapshotDefinition;
import com.linbit.linstor.core.objects.SnapshotVolumeDefinition;
import com.linbit.linstor.core.repository.ResourceDefinitionRepository;
import com.linbit.linstor.security.AccessContext;
import com.linbit.linstor.security.AccessDeniedException;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Singleton
public class CtrlSnapshotApiCallHandler
{
    private final ResourceDefinitionRepository resourceDefinitionRepository;
    private final Provider<AccessContext> peerAccCtx;

    @Inject
    public CtrlSnapshotApiCallHandler(
        ResourceDefinitionRepository resourceDefinitionRepositoryRef,
        @PeerContext Provider<AccessContext> peerAccCtxRef
    )
    {
        resourceDefinitionRepository = resourceDefinitionRepositoryRef;
        peerAccCtx = peerAccCtxRef;
    }

    private boolean shouldIncludeSnapshot(
        final SnapshotDefinitionListItemApi snapItem,
        final List<String> nodeNameFilter
    )
    {
        boolean includeFlag = nodeNameFilter.isEmpty();
        if (!includeFlag)
        {
            for (final String node : nodeNameFilter)
            {
                for (final String snapNode : snapItem.getNodeNames())
                {
                    if (node.equalsIgnoreCase(snapNode))
                    {
                        includeFlag = true;
                        break;
                    }
                }
            }
        }
        return includeFlag;
    }

    ArrayList<SnapshotDefinitionListItemApi> listSnapshotDefinitions(List<String> nodeNames, List<String> resourceNames)
    {
        ArrayList<SnapshotDefinitionListItemApi> snapshotDfns = new ArrayList<>();
        final Set<ResourceName> rscDfnsFilter =
            resourceNames.stream().map(LinstorParsingUtils::asRscName).collect(Collectors.toSet());

        try
        {
            for (ResourceDefinition rscDfn : resourceDefinitionRepository.getMapForView(peerAccCtx.get()).values())
            {
                if (rscDfnsFilter.isEmpty() || rscDfnsFilter.contains(rscDfn.getName()))
                {
                    for (SnapshotDefinition snapshotDfn : rscDfn.getSnapshotDfns(peerAccCtx.get()))
                    {
                        try
                        {
                            final SnapshotDefinitionListItemApi snapItem =
                                snapshotDfn.getListItemApiData(peerAccCtx.get());
                            if (shouldIncludeSnapshot(snapItem, nodeNames))
                            {
                                snapshotDfns.add(snapItem);
                            }
                        }
                        catch (AccessDeniedException accDeniedExc)
                        {
                            // don't add snapshot definition without access
                        }
                    }
                }
            }
        }
        catch (AccessDeniedException accDeniedExc)
        {
            // for now return an empty list.
        }

        return snapshotDfns;
    }

    public static String getSnapshotDescription(
        List<String> nodeNameStrs,
        String rscNameStr,
        String snapshotNameStr
    )
    {
        String snapshotDescription = "Resource: " + rscNameStr + ", Snapshot: " + snapshotNameStr;
        return nodeNameStrs.isEmpty() ?
            snapshotDescription :
            "Nodes: " + String.join(", ", nodeNameStrs) + "; " + snapshotDescription;
    }

    public static String getSnapshotDescriptionInline(Snapshot snapshot)
    {
        return getSnapshotDescriptionInline(
            Collections.singletonList(snapshot.getNode().getName().displayValue),
            snapshot.getResourceName().displayValue,
            snapshot.getSnapshotName().displayValue
        );
    }

    public static String getSnapshotDescriptionInline(
        List<String> nodeNameStrs,
        String rscNameStr,
        String snapshotNameStr
    )
    {
        String snapshotDescription = getSnapshotDfnDescriptionInline(rscNameStr, snapshotNameStr);
        return nodeNameStrs.isEmpty() ?
            snapshotDescription :
            snapshotDescription + " on nodes '" + String.join(", ", nodeNameStrs) + "'";
    }

    public static String getSnapshotDfnDescription(String rscNameStr, String snapshotName)
    {
        return "Snapshot definition " + snapshotName + " of resource " + rscNameStr;
    }

    public static String getSnapshotDfnDescriptionInline(SnapshotDefinition snapshotDfn)
    {
        return getSnapshotDfnDescriptionInline(snapshotDfn.getResourceName(), snapshotDfn.getName());
    }

    public static String getSnapshotDfnDescriptionInline(
        ResourceName rscName,
        SnapshotName snapshotName
    )
    {
        return getSnapshotDfnDescriptionInline(rscName.displayValue, snapshotName.displayValue);
    }

    public static String getSnapshotDfnDescriptionInline(
        String rscNameStr,
        String snapshotNameStr
    )
    {
        return "snapshot '" + snapshotNameStr + "' of resource '" + rscNameStr + "'";
    }

    public static String getSnapshotVlmDfnDescriptionInline(SnapshotVolumeDefinition snapshotVlmDfn)
    {
        return getSnapshotVlmDfnDescriptionInline(
            snapshotVlmDfn.getResourceName().displayValue,
            snapshotVlmDfn.getSnapshotName().displayValue,
            snapshotVlmDfn.getVolumeNumber().value
        );
    }

    public static String getSnapshotVlmDfnDescriptionInline(
        String rscNameStr,
        String snapshotNameStr,
        Integer vlmNr
    )
    {
        return "volume definition with number '" + vlmNr +
            "' of snapshot '" + snapshotNameStr + "' of resource '" + rscNameStr + "'";
    }

    public static String getSnapshotVlmDescriptionInline(
        NodeName nodeName,
        ResourceName resourceName,
        SnapshotName snapshotName,
        VolumeNumber volumeNumber
    )
    {
        return "volume with number '" + volumeNumber.value +
            "' of snapshot '" + snapshotName + "' of resource '" + resourceName + "' on '" + nodeName + "'";
    }

    public static ResponseContext makeSnapshotContext(
        ApiOperation operation,
        List<String> nodeNameStrs,
        String rscNameStr,
        String snapshotNameStr
    )
    {
        Map<String, String> objRefs = new TreeMap<>();
        objRefs.put(ApiConsts.KEY_RSC_DFN, rscNameStr);
        objRefs.put(ApiConsts.KEY_SNAPSHOT, snapshotNameStr);

        return new ResponseContext(
            operation,
            getSnapshotDescription(nodeNameStrs, rscNameStr, snapshotNameStr),
            getSnapshotDescriptionInline(nodeNameStrs, rscNameStr, snapshotNameStr),
            ApiConsts.MASK_SNAPSHOT,
            objRefs
        );
    }
}
