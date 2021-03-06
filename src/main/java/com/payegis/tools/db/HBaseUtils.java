package com.payegis.tools.db;

import com.payegis.tools.encrypt.MD5Utils;
import com.payegis.tools.util.ExternalPropertyUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.sf.json.JSONObject;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.filter.*;
import org.apache.hadoop.hbase.io.compress.Compression;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.log4j.Logger;

import java.io.*;
import java.util.*;

/**
 * Company:
 * User: 陈作立
 * Date: 2018/3/29 9:20
 * Description: Java操作HBase工具类
 * Ps: Java HBase
 */
public class HBaseUtils implements Serializable{
    public static Configuration conf;
    public static Connection connection;
    public static Admin admin;
    public static Properties props;
    private static Logger logger = Logger.getLogger(HBaseUtils.class);

    /**
     * description: 获取HBaseUtils工具类的实例，传递连接hbase配置文件路径作为参数
     * param: [profilePath]
     * return: com.payegis.tools.db.HBaseUtils
     * date: 2018/6/13
     * time: 14:23
     */
    public static HBaseUtils getInstance(String profilePath){
        try {
            ExternalPropertyUtils instance = ExternalPropertyUtils.getInstance(profilePath);
            props = instance.props;
            conf = HBaseConfiguration.create();
            conf.set("hbase.zookeeper.property.clientPort", props.getProperty("zookeeperPort"));
            conf.set("hbase.zookeeper.quorum", props.getProperty("zookeeperHost"));
            connection = ConnectionFactory.createConnection(conf);
            admin = connection.getAdmin();
            logger.info("初始化hbase连接成功!");
        } catch (Exception e) {
            logger.error("初始化hbase连接异常!", e);
        }
        return new HBaseUtils();
    }

    private HBaseUtils(){}

    /**
     * Description: 获取hbase Configuration对象
     * Param: []
     * Return: org.apache.hadoop.conf.Configuration
     * Date: 2018/4/12
     * Time: 14:07
     */
    public Configuration getConf(String profilePath) {
        if (conf == null) {
            getInstance(profilePath);
        }
        return conf;
    }

    /**
     * Description: 获取hbase连接
     * Param: []
     * Return: org.apache.hadoop.hbase.client.Connection
     * Date: 2018/4/12
     * Time: 14:08
     */
    public Connection getConnection(String profilePath) {
        if (connection == null) {
            try {
                getConf(profilePath);
                connection = ConnectionFactory.createConnection(conf);
            } catch (IOException e) {
                logger.error("get connection of hbase exception!", e);
            }
        }
        return connection;
    }

    /**
     * Description: 获取hbase表操作对象
     * Param: []
     * Return: org.apache.hadoop.hbase.client.Admin
     * Date: 2018/4/12
     * Time: 14:10
     */
    public Admin getAdmin(String profilePath) {
        if (admin == null) {
            try {
                getConnection(profilePath);
                admin = connection.getAdmin();
            } catch (IOException e) {
                logger.error("get hbase admin exception!", e);
            }
        }
        return admin;
    }

    /**
     * description: 获取hbase table
     * param: [tableName]
     * return: org.apache.hadoop.hbase.client.Table
     * date: 2018/6/13
     * time: 14:23
     */
    public Table getTable(String tableName) {
        Table table = null;
        try {
            table = connection.getTable(TableName.valueOf(tableName));
        } catch (IOException e) {
            logger.error("get hbase table " + tableName + " exception!", e);
        }
        return table;
    }

    /**
     * Description: 建表，如果表存在，那么不创建。如果未指定列族名称，默认定义一个cf1
     * Param: [tableName, familyName]
     * Return: boolean
     * Date: 2018/3/29
     * Time: 9:24
     */
    public boolean createTable(String tableName, String familyName) {
        boolean flag = false;
        if (familyName == null || familyName.length() == 0) {
            familyName = "cf1";
        }
        TableName tbl = TableName.valueOf(tableName);
        try {
            Admin admin = connection.getAdmin();
            if (admin.tableExists(tbl)) {
                logger.info("Table " + tbl.getNameAsString() + " is already exists!");
                return flag;
            }
            HTableDescriptor tableDescriptor = new HTableDescriptor(tbl);
            tableDescriptor.addFamily(new HColumnDescriptor(familyName).setCompressionType(Compression.Algorithm.SNAPPY));
            admin.createTable(tableDescriptor);
            logger.info("Create table " + tbl.getNameAsString() + " success!");
            flag = true;
        } catch (Exception e) {
            logger.error("create hbase table " + tableName + ", family name " + familyName + " failed!", e);
        }
        return flag;
    }

    /**
     * Description: 插入一条数据到hbase
     * Param: [connection, tableName, rowkey, columnFamily, key, value]
     * Return: void
     * Date: 2018/3/28
     * Time: 14:06
     */
    public void insertOne(String tableName, String rowkey, String columnFamily, String key, String value) {
        Table table = null;
        try {
            table = connection.getTable(TableName.valueOf(tableName));
            Put put = new Put(Bytes.toBytes(rowkey));
            put.addColumn(Bytes.toBytes(columnFamily), Bytes.toBytes(key), Bytes.toBytes(value));
            table.put(put);
        } catch (Exception e) {
            logger.error("insert to hbase table exception! " + "rowkey: " + rowkey + ", column family name: " + columnFamily + ", key: " + key + ", value: " + value, e);
        } finally {
            closeTableAndResult(table, null);
        }
    }

    /**
     * Description: 批量插入数据到hbase
     * Param: [filePath, tableName, familyName]
     * Return: void
     * Date: 2018/3/30
     * Time: 13:31
     */
    public void insertBatch(String filePath, String tableName, String familyName) {
        ObjectArrayList<Put> puts = new ObjectArrayList<>();
        Table table = null;
        FileInputStream fis = null;
        BufferedReader br = null;
        try {
            table = connection.getTable(TableName.valueOf(tableName));
            fis = new FileInputStream(filePath);
            br = new BufferedReader(new InputStreamReader(fis));
            String line = br.readLine();
            while (line != null) {
                JSONObject lineJsonObject = JSONObject.fromObject(line);
                String rowkey = MD5Utils.strToMd5_16(UUID.randomUUID().toString());
                Set<String> keys = lineJsonObject.keySet();
                Put put = new Put(Bytes.toBytes(rowkey));
                for (String key : keys) {
                    put.addColumn(Bytes.toBytes(familyName), Bytes.toBytes(key), Bytes.toBytes(lineJsonObject.optString(key)));
                    puts.add(put);
                }
                line = br.readLine();
            }
            table.put(puts);
        } catch (Exception e) {
            logger.error("insert batch data to hbase table exception! " + "file path: " + filePath + ", table name: " + tableName + ", family name: " + familyName, e);
        } finally {
            try {
                if (table != null) table.close();
                if (fis != null) fis.close();
                if (br != null) br.close();
            } catch (IOException e) {
                logger.error("close hbase table or stream exception!", e);
            }
        }
    }

    /**
     * Description: 批量插入数据到hbase
     * Param: [rows, tableName, familyName]
     * Return: void
     * Date: 2018/4/2
     * Time: 10:19
     */
    public void insertBatch(List<Map<String, Object>> rows, String tableName, String familyName) {
        ObjectArrayList<Put> puts = new ObjectArrayList<>();
        Table table = null;
        try {
            table = connection.getTable(TableName.valueOf(tableName));
            for (Map<String, Object> row : rows) {
                String rowkey = MD5Utils.strToMd5_16(UUID.randomUUID().toString());
                Put put = new Put(Bytes.toBytes(rowkey));
                for (Map.Entry<String, Object> kv : row.entrySet()) {
                    String key = kv.getKey();
                    Object value = kv.getValue();
                    if (value == null) {
                        put.addColumn(Bytes.toBytes(familyName), Bytes.toBytes(key), null);
                    } else {
                        put.addColumn(Bytes.toBytes(familyName), Bytes.toBytes(key), Bytes.toBytes(value.toString()));
                    }
                }
                puts.add(put);
            }
            table.put(puts);
        } catch (Exception e) {
            logger.error("insert batch data to hbase table exception! " + "rows: " + rows + ", table name: " + tableName + ", family name: " + familyName, e);
        } finally {
            closeTableAndResult(table, null);
        }
    }

    /**
     * description: 批量插入数据到hbase，指定rowkey前缀，后缀为随机UUID
     * param: [rows, tableName, familyName, rowkeyPrefix]
     * return: void
     * date: 2018/6/13
     * time: 14:24
     */
    public void insertBatch(List<Map<String, Object>> rows, String tableName, String familyName, String rowkeyPrefix) {
        ObjectArrayList<Put> puts = new ObjectArrayList<>();
        Table table = null;
        try {
            table = connection.getTable(TableName.valueOf(tableName));
            for (Map<String, Object> row : rows) {
                String rowkey = rowkeyPrefix + MD5Utils.strToMd5_16(UUID.randomUUID().toString());
                Put put = new Put(Bytes.toBytes(rowkey));
                for (Map.Entry<String, Object> kv : row.entrySet()) {
                    String key = kv.getKey();
                    Object value = kv.getValue();
                    if (value == null) {
                        put.addColumn(Bytes.toBytes(familyName), Bytes.toBytes(key), null);
                    } else {
                        put.addColumn(Bytes.toBytes(familyName), Bytes.toBytes(key), Bytes.toBytes(value.toString()));
                    }
                }
                puts.add(put);
            }
            table.put(puts);
        } catch (Exception e) {
            logger.error("insert batch data to hbase table exception! " + "rows: " + rows + ", table name: " + tableName + ", family name: " + familyName, e);
        } finally {
            closeTableAndResult(table, null);
        }
    }

    /**
     * Description: 删除一张表
     * Param: [tableName]
     * Return: boolean
     * Date: 2018/3/29
     * Time: 9:40
     */
    public boolean dropTable(String tableName) {
        boolean flag = false;
        try {
            admin.disableTable(TableName.valueOf(tableName));
            admin.deleteTable(TableName.valueOf(tableName));
            flag = true;
        } catch (Exception e) {
            logger.error("delete hbase table " + tableName + " exception!", e);
        }
        return flag;
    }

    /**
     * Description: 根据rowkey删除一条记录
     * Param: [tablename, rowkey]
     * Return: boolean
     * Date: 2018/3/29
     * Time: 9:40
     */
    public boolean deleteOneRowByRowkey(String tablename, String rowkey) {
        boolean flag = false;
        Table table = null;
        try {
            table = connection.getTable(TableName.valueOf(tablename));
            Delete d = new Delete(rowkey.getBytes());
            table.delete(d);
            logger.info("delete row " + rowkey + " success!");
            flag = true;
        } catch (Exception e) {
            logger.error("delete hbase table " + tablename + "'s row " + rowkey + " exception!", e);
        }
        closeTableAndResult(table, null);
        return flag;
    }

    /**
     * Description: 批量删除rowkey
     * Param: [tablename, rowkeyList]
     * Return: boolean
     * Date: 2018/3/29
     * Time: 9:47
     */
    public boolean deleteBatchRowByRowkey(String tablename, List<String> rowkeyList) {
        boolean flag = false;
        ObjectArrayList<Delete> listDelete = new ObjectArrayList<>();
        Table table = null;
        try {
            table = connection.getTable(TableName.valueOf(tablename));
            for (int i = 0; i < rowkeyList.size(); i++) {
                Delete delete = new Delete(rowkeyList.get(i).getBytes());
                listDelete.add(delete);
            }
            table.delete(listDelete);
            logger.info("delete row list " + rowkeyList + " success!");
            flag = true;
        } catch (Exception e) {
            logger.error("delete hbase table " + tablename + "'s rows exception!", e);
        }
        closeTableAndResult(table, null);
        return flag;
    }

    /**
     * Description: 查询表中所有数据
     * Param: [tableName]
     * Return: List<HashMap<String,HashMap<String,String>>>
     * Date: 2018/3/29
     * Time: 9:51
     */
    public List<HashMap<String, HashMap<String, String>>> queryAll(String tableName) {
        ObjectArrayList<HashMap<String, HashMap<String, String>>> rowMapList = new ObjectArrayList<>(); // <familyName, <columnName, columnValue>>
        Table table = null;
        ResultScanner rs = null;
        try {
            table = connection.getTable(TableName.valueOf(tableName));
//            ResultScanner rs = table.getScanner(new Scan().setMaxVersions()); // 获取所有版本数据
            rs = table.getScanner(new Scan());
            for (Result r : rs) {
                rowMapList.add(resolveResult(r));
            }
        } catch (Exception e) {
            logger.error("get hbase table " + tableName + " all data exception!", e);
        } finally {
            closeTableAndResult(table, rs);
        }
        return rowMapList;
    }

    /**
     * Description: 单条件查询, 根据rowkey查询唯一一条记录
     * Param: [tableName, rowKey]
     * Return: List<HashMap<String,HashMap<String,String>>>
     * Date: 2018/3/29
     * Time: 10:47
     */
    public List<HashMap<String, HashMap<String, String>>> queryByCondition(String tableName, String rowKey) {
        ObjectArrayList<HashMap<String, HashMap<String, String>>> rowMapList = new ObjectArrayList<>(); // <familyName, <columnName, columnValue>>
        Table table = null;
        try {
            Get get = new Get(rowKey.getBytes());
//            get.setMaxVersions(); // 获取所有版本数据
            table = connection.getTable(TableName.valueOf(tableName));
            Result r = table.get(get);
            rowMapList.add(resolveResult(r));
            logger.info("获得到rowkey: " + new String(r.getRow()));
        } catch (IOException e) {
            logger.error("get hbase table " + tableName + "'s rowkey " + rowKey  + " exception!", e);
        } finally {
            closeTableAndResult(table, null);
        }
        return rowMapList;
    }

    /**
     * Description: 单条件按查询，查询多条记录
     * Param: [tableName]
     * Return: List<HashMap<String,HashMap<String,String>>>
     * Date: 2018/3/29
     * Time: 13:16
     */
    public List<HashMap<String, HashMap<String, String>>> queryByCondition(String tableName, String familyName, String columnName, String columnValue) {
        ObjectArrayList<HashMap<String, HashMap<String, String>>> rowMapList = new ObjectArrayList<>(); // <familyName, <columnName, columnValue>>
        Table table = null;
        ResultScanner rs = null;
        try {
            table = connection.getTable(TableName.valueOf(tableName));
            Filter filter = new SingleColumnValueFilter(Bytes.toBytes(familyName), Bytes.toBytes(columnName), CompareFilter.CompareOp.EQUAL, Bytes.toBytes(columnValue)); // 当列columnName的值为columnValue时进行查询
            Scan s = new Scan();
            s.setFilter(filter);
            rs = table.getScanner(s);
            for (Result r : rs) {
                rowMapList.add(resolveResult(r));
            }
        } catch (Exception e) {
            logger.error("query hbase table " + tableName + " with one condition: column name is " + columnName + ", column value is " + columnValue + " exception!", e);
        } finally {
            closeTableAndResult(table, rs);
        }
        return rowMapList;
    }

    /**
     * Description: 组合条件查询
     * Param: [tableName]
     * Return: List<HashMap<String,HashMap<String,String>>>
     * Date: 2018/3/29
     * Time: 13:26
     */
    public List<HashMap<String, HashMap<String, String>>> queryByCondition(String tableName, String familyName, HashMap<String, String> paramMap) {
        ObjectArrayList<HashMap<String, HashMap<String, String>>> rowMapList = new ObjectArrayList<>(); // <familyName, <columnName, columnValue>>
        Table table = null;
        ResultScanner rs = null;
        try {
            table = connection.getTable(TableName.valueOf(tableName));
            FilterList filterList = new FilterList();
            for (Map.Entry<String, String> entry : paramMap.entrySet()) {
                Filter filter = new SingleColumnValueFilter(Bytes.toBytes(familyName), Bytes.toBytes(entry.getKey()), CompareFilter.CompareOp.EQUAL, Bytes.toBytes(entry.getValue()));
                filterList.addFilter(filter);
            }
            Scan scan = new Scan();
            scan.setFilter(filterList);
            rs = table.getScanner(scan);
            for (Result r : rs) {
                rowMapList.add(resolveResult(r));
            }
            rs.close();
        } catch (Exception e) {
            logger.error("query hbase table " + tableName + " with many condition: column names and values is " + paramMap + " exception!", e);
        } finally {
            closeTableAndResult(table, rs);
        }
        return rowMapList;
    }

    /**
     * Description: 查询hbase，匹配rowkey前缀为prefix的行
     * Param: [tableName]
     * Return: java.util.List<java.util.HashMap<java.lang.String,java.util.HashMap<java.lang.String,java.lang.String>>>
     * Date: 2018/4/3
     * Time: 20:21
     */
    public List<HashMap<String, HashMap<String, String>>> rowkeyFuzzyQuery(String tableName, String prefix) {
        ObjectArrayList<HashMap<String, HashMap<String, String>>> rowMapList = new ObjectArrayList<>();
        Table table = null;
        ResultScanner rs = null;
        try {
            table = connection.getTable(TableName.valueOf(tableName));
            Scan scan = new Scan();
            Filter filter = new RowFilter(CompareFilter.CompareOp.EQUAL, new RegexStringComparator(prefix + ".*"));
            scan.setFilter(filter);
            rs = table.getScanner(scan);
            for (Result r : rs) {
                rowMapList.add(resolveResult(r));
            }
        } catch (Exception e) {
            logger.error("get hbase table " + tableName + " and rowkey prefix is " + prefix + " exception!", e);
        } finally {
            closeTableAndResult(table, rs);
        }
        return rowMapList;
    }

    /**
     * Description: 解析查询hbase得到的结果，放入到HashMap中
     * Param: [result]
     * Return: java.util.HashMap<String,HashMap<String,String>>
     * Date: 2018/3/29
     * Time: 13:52
     */
    public HashMap<String, HashMap<String, String>> resolveResult(Result result) {
        HashMap<String, HashMap<String, String>> rowMap = new HashMap<>(); // <familyName, <columnName, columnValue>>
        HashMap<String, String> kvMap = new HashMap<>();
        NavigableMap<byte[], NavigableMap<byte[], NavigableMap<Long, byte[]>>> map = result.getMap();
        for (Map.Entry<byte[], NavigableMap<byte[], NavigableMap<Long, byte[]>>> entry : map.entrySet()) {
            String familyName = new String(entry.getKey());
            NavigableMap<byte[], NavigableMap<Long, byte[]>> valueInfoMap = entry.getValue();
            for (Map.Entry<byte[], NavigableMap<Long, byte[]>> valueInfo : valueInfoMap.entrySet()) {
                String key = new String(valueInfo.getKey());
                NavigableMap<Long, byte[]> values = valueInfo.getValue();
                Map.Entry<Long, byte[]> firstEntry = values.firstEntry();
                Long timestampLastest = firstEntry.getKey();
                String valueLastest = new String(firstEntry.getValue());
                logger.info("familyName: " + familyName + ", key: " + key + ", value: " + valueLastest + ", timestamp: " + timestampLastest);
//                for (Map.Entry<Long, byte[]> vals : values.entrySet()) {
//                    Long timestamp = vals.getKey();
//                    String value = new String(vals.getValue());
//                }
                kvMap.put(key, valueLastest);
                rowMap.put(familyName, kvMap);
            }
        }
        return rowMap;
    }

    /**
     * Description: 关闭表和结果集
     * Param: [table, rs]
     * Return: void
     * Date: 2018/4/4
     * Time: 15:55
     */
    private void closeTableAndResult(Table table, ResultScanner rs) {
        try {
            if (rs != null) rs.close();
            if (table != null) table.close();
        } catch (IOException e) {
            logger.error("close table failed!");
            e.printStackTrace();
        }
    }

}
