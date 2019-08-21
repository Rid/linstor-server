package com.linbit.linstor.dbdrivers.etcd;

import static com.ibm.etcd.client.KeyUtils.bs;

import com.linbit.linstor.dbdrivers.GeneratedDatabaseTables.Column;
import com.linbit.linstor.dbdrivers.GeneratedDatabaseTables.Table;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.ibm.etcd.api.KeyValue;
import com.ibm.etcd.api.PutRequest;
import com.ibm.etcd.api.RangeResponse;
import com.ibm.etcd.client.kv.KvClient;

public class EtcdUtils
{
    public static final String PATH_DELIMITER = "/";
    public static final String PK_DELIMITER = ":";
    public static final String LINSTOR_PREFIX = "LINSTOR" + PATH_DELIMITER;

    public static PutRequest putReq(String key, String value)
    {
        return PutRequest.newBuilder().setKey(bs(key)).setValue(bs(value)).build();
    }

    public static String buildKey(
        Table table,
        String... pks
    )
    {
        StringBuilder sb = new StringBuilder();
        sb.append(LINSTOR_PREFIX).append(table.getName()).append(PATH_DELIMITER);
        if (pks.length > 0)
        {
            for (String pk : pks)
            {
                sb.append(pk).append(PK_DELIMITER);
            }
            sb.setLength(sb.length() - PK_DELIMITER.length()); // cut last PK_DELIMITER
            sb.append(PATH_DELIMITER);
        }
        return sb.toString();
    }

    public static Map<String, String> getTableRow(KvClient client, String key)
    {
        RangeResponse rspRow = client.get(bs(key)).asPrefix().sync();

        HashMap<String, String> rowMap = new HashMap<>();
        for (KeyValue keyValue : rspRow.getKvsList())
        {
            final String recKey = keyValue.getKey().toStringUtf8();
            final String columnName = recKey.substring(recKey.lastIndexOf("/") + 1);
            rowMap.put(columnName, keyValue.getValue().toStringUtf8());
        }

        return rowMap;
    }

    public static String getFirstValue(KvClient kvClientRef, String primaryKey)
    {
        return getFirstValue(kvClientRef, primaryKey, null);
    }

    public static String getFirstValue(KvClient kvClientRef, String primaryKeyRef, String dfltValue)
    {
        Map<String, String> row = getTableRow(kvClientRef, primaryKeyRef);
        Iterator<String> iterator = row.values().iterator();
        String ret = dfltValue;
        if (iterator.hasNext())
        {
            ret = iterator.next();
        }
        return ret;
    }

    public static String getTablePk(Column col, String... pks)
    {
        StringBuilder ret = new StringBuilder();
        ret.append(LINSTOR_PREFIX).append(col.getTable().getName()).append(PATH_DELIMITER);
        if (pks != null)
        {
            for (String pk : pks)
            {
                ret.append(pk).append(PATH_DELIMITER);
            }
            if (pks.length > 0)
            {
                ret.setLength(ret.length() - PATH_DELIMITER.length());
            }
        }
        return ret.toString();
    }

    public static Set<String> getComposedPkList(Map<String, String> tableRowRef)
    {
        Set<String> ret = new TreeSet<>();
        for (String key : tableRowRef.keySet())
        {
            // key is something like
            // LINSTOR/$table/$composedPk/$column = $valueOfColumn
            int tableStartIdx = key.indexOf(PATH_DELIMITER);
            int composedKeyStartIdx = key.indexOf(PATH_DELIMITER, tableStartIdx + 1);
            int composedKeyEndIdx = key.lastIndexOf(PATH_DELIMITER);

            String composedKey = key.substring(composedKeyStartIdx + 1, composedKeyEndIdx);

            ret.add(composedKey);
        }
        return ret;
    }
}
