/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cassandra.pig;

import java.io.IOException;

import org.apache.cassandra.db.marshal.TimeUUIDType;
import org.apache.cassandra.db.marshal.UUIDType;
import org.apache.cassandra.exceptions.ConfigurationException;
import org.apache.cassandra.utils.Hex;
import org.apache.pig.data.DataByteArray;
import org.apache.pig.data.Tuple;
import org.junit.BeforeClass;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;

public class ThriftColumnFamilyDataTypeTest extends PigTestBase
{
    private static String[] statements = {
            "DROP KEYSPACE IF EXISTS thrift_ks",
            "CREATE KEYSPACE thrift_ks WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};",
            "USE thrift_ks;",

            "CREATE TABLE some_app (" +
            "key text PRIMARY KEY," +
            "col_ascii ascii," +
            "col_bigint bigint," +
            "col_blob blob," +
            "col_boolean boolean," +
            "col_decimal decimal," +
            "col_double double," +
            "col_float float," +
            "col_inet inet," +
            "col_int int," +
            "col_text text," +
            "col_timestamp timestamp," +
            "col_timeuuid timeuuid," +
            "col_uuid uuid," +
            "col_varint varint)" +
            " WITH COMPACT STORAGE;",

            "INSERT INTO some_app (key, col_ascii, col_bigint, col_blob, col_boolean, col_decimal, col_double, col_float," +
                "col_inet, col_int, col_text, col_timestamp, col_uuid, col_varint, col_timeuuid) " +
                    "VALUES ('foo', 'ascii', 12345678, 0xDEADBEEF, false, 23.345, 2.7182818284590451, 23.45, '127.0.0.1', 23, 'hello', " +
                        "'2011-02-03T04:05:00+0000', 550e8400-e29b-41d4-a716-446655440000, 12345, e23f450f-53a6-11e2-7f7f-7f7f7f7f7f7f);",

            "CREATE TABLE cc (key text, name text, value counter, PRIMARY KEY (key, name)) WITH COMPACT STORAGE",

            "UPDATE cc SET value = value + 3 WHERE key = 'chuck' AND name = 'kick'",
    };

    @BeforeClass
    public static void setup() throws IOException, ConfigurationException
    {
        startCassandra();
        executeCQLStatements(statements);
        startHadoopCluster();
    }

    @Test
    public void testCassandraStorageDataType() throws IOException
    {
        pig.registerQuery("rows = LOAD 'cql://thrift_ks/some_app?" + defaultParameters + "' USING CqlNativeStorage();");
        Tuple t = pig.openIterator("rows").next();

        // key
        assertEquals("foo", t.get(0));

        // col_ascii
        Object column = t.get(1);
        assertEquals("ascii", column);

        // col_bigint
        column = t.get(2);
        assertEquals(12345678L, column);

        // col_blob
        column = t.get(3);
        assertEquals(new DataByteArray(Hex.hexToBytes("DEADBEEF")), column);

        // col_boolean
        column = t.get(4);
        assertEquals(false, column);

        // col_decimal
        column = t.get(5);
        assertEquals("23.345", column);

        // col_double
        column = t.get(6);
        assertEquals(2.7182818284590451d, column);

        // col_float
        column = t.get(7);
        assertEquals(23.45f, column);

        // col_inet
        column = t.get(8);
        assertEquals("127.0.0.1", column);

        // col_int
        column = t.get(9);
        assertEquals(23, column);

        // col_text
        column = t.get(10);
        assertEquals("hello", column);

        // col_timestamp
        column = t.get(11);
        assertEquals(1296705900000L, column);

        // col_timeuuid
        column = t.get(12);
        assertEquals(new DataByteArray((TimeUUIDType.instance.fromString("e23f450f-53a6-11e2-7f7f-7f7f7f7f7f7f").array())), column);

        // col_uuid
        column = t.get(13);
        assertEquals(new DataByteArray((UUIDType.instance.fromString("550e8400-e29b-41d4-a716-446655440000").array())), column);

        // col_varint
        column = t.get(14);
        assertEquals(12345, column);

        pig.registerQuery("cc_rows = LOAD 'cql://thrift_ks/cc?" + defaultParameters + "' USING CqlNativeStorage();");
        t = pig.openIterator("cc_rows").next();

        assertEquals("chuck", t.get(0));

        assertEquals("kick", t.get(1));
        assertEquals(3L, t.get(2));
    }
}
