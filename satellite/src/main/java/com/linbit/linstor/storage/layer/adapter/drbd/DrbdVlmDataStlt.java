package com.linbit.linstor.storage.layer.adapter.drbd;

import com.linbit.linstor.InternalApiConsts;
import com.linbit.linstor.storage2.layer.data.DrbdVlmData;
import com.linbit.linstor.storage2.layer.data.State;

import java.util.ArrayList;
import java.util.List;

public class DrbdVlmDataStlt implements DrbdVlmData
{
    boolean exists;
    boolean failed;
    String metaDiskPath;
    String diskState;

    transient short peerSlots;
    transient int alStripes;
    transient long alStripeSize;
    transient boolean hasMetaData;
    transient boolean checkMetaData;
    transient boolean metaDataIsNew;
    transient boolean hasDisk;

    List<State> state = new ArrayList<>();

    public DrbdVlmDataStlt()
    {
        exists = false;
        failed = false;
        metaDiskPath = null;

        peerSlots = InternalApiConsts.DEFAULT_PEER_SLOTS;
        alStripes = -1;
        alStripeSize = -1L;

        checkMetaData = true;
        metaDataIsNew = false;
    }


    @Override
    public boolean exists()
    {
        return exists;
    }

    @Override
    public boolean isFailed()
    {
        return failed;
    }

    @Override
    public String getMetaDiskPath()
    {
        return metaDiskPath;
    }

    @Override
    public String getDiskState()
    {
        return diskState;
    }

    @Override
    public List<? extends State> getStates()
    {
        return state;
    }
}