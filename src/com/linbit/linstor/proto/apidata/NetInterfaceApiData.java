/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.linbit.linstor.proto.apidata;

import com.google.protobuf.ByteString;
import com.linbit.linstor.NetInterface;
import com.linbit.linstor.proto.NetInterfaceOuterClass;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 *
 * @author rpeinthor
 */
public class NetInterfaceApiData implements NetInterface.NetInterfaceApi {
    private final NetInterfaceOuterClass.NetInterface netInterface;

    public NetInterfaceApiData(final NetInterfaceOuterClass.NetInterface refNetInterface)
    {
        netInterface = refNetInterface;
    }

    @Override
    public UUID getUuid() {
        if(netInterface.hasUuid())
            return UUID.nameUUIDFromBytes(netInterface.getUuid().toByteArray());
        return null;
    }

    @Override
    public String getName() {
        return netInterface.getName();
    }

    @Override
    public String getAddress() {
        return netInterface.getAddress();
    }

    @Override
    public int getPort() {
        return netInterface.getPort();
    }

    @Override
    public String getType() {
        return netInterface.getType();
    }

    public static List<NetInterfaceOuterClass.NetInterface> toNetInterfaceProtoList(
        List<NetInterface.NetInterfaceApi> netInterfaceApiList)
    {
        ArrayList<NetInterfaceOuterClass.NetInterface> resultList = new ArrayList<>();
        for(NetInterface.NetInterfaceApi netInterApi : netInterfaceApiList)
        {
            resultList.add(toNetInterfaceProto(netInterApi));
        }
        return resultList;
    }

    public static NetInterfaceOuterClass.NetInterface toNetInterfaceProto(
            final NetInterface.NetInterfaceApi netInterApi)
    {
        NetInterfaceOuterClass.NetInterface.Builder bld = NetInterfaceOuterClass.NetInterface.newBuilder();
        bld.setUuid(ByteString.copyFrom(netInterApi.getUuid().toString().getBytes()));
        bld.setName(netInterApi.getName());
        bld.setAddress(netInterApi.getAddress());
        bld.setType(netInterApi.getType());
        bld.setPort(netInterApi.getPort());
        return bld.build();
    }

}
