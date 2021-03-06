/*
 * Copyright 2016 Confluent Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.confluent.connect.jdbc.sink;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.sink.SinkRecord;
import org.apache.kafka.connect.sink.SinkTaskContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class SinkTaskTest {
  private final SqliteHelper sqliteHelper = new SqliteHelper(getClass().getSimpleName());

  @Before
  public void setUp() throws IOException, SQLException {
    sqliteHelper.setUp();
  }

  @After
  public void tearDown() throws IOException, SQLException {
    sqliteHelper.tearDown();
  }

  @Test
  public void putPropagatesToDb() throws Exception {
    final String tableName1 = "table1";
    final String tableName2 = "table2";

    Map<String, String> props = new HashMap<>();
    props.put("connection.url", sqliteHelper.sqliteUri());
    props.put("auto.create", "true");
    props.put(String.format("%s.pk.mode", tableName1), "kafka");
    props.put(String.format("%s.pk.fields", tableName1), "kafka_topic,kafka_partition,kafka_offset");
    props.put(String.format("%s.pk.mode", tableName2), "record_value");
    props.put(String.format("%s.pk.fields", tableName2), "firstName,lastName");

    JdbcSinkTask task = new JdbcSinkTask();
    task.initialize(mock(SinkTaskContext.class));

    String createTable2 = "CREATE TABLE " + tableName2 + "(" +
                          "    firstName  TEXT," +
                          "    lastName  TEXT," +
                          "    age INTEGER," +
                          "    bool  NUMERIC," +
                          "    byte  INTEGER," +
                          "    short INTEGER NULL," +
                          "    long INTEGER," +
                          "    float NUMERIC," +
                          "    double NUMERIC," +
                          "    bytes BLOB, " +
                          "PRIMARY KEY (firstName, lastName));";

    sqliteHelper.deleteTable(tableName1);
    sqliteHelper.deleteTable(tableName2);
    sqliteHelper.createTable(createTable2);

    task.start(props);

    Schema schema = SchemaBuilder.struct().name("com.example.Person")
        .field("firstName", Schema.STRING_SCHEMA)
        .field("lastName", Schema.STRING_SCHEMA)
        .field("age", Schema.OPTIONAL_INT32_SCHEMA)
        .field("bool", Schema.OPTIONAL_BOOLEAN_SCHEMA)
        .field("short", Schema.OPTIONAL_INT16_SCHEMA)
        .field("byte", Schema.OPTIONAL_INT8_SCHEMA)
        .field("long", Schema.OPTIONAL_INT64_SCHEMA)
        .field("float", Schema.OPTIONAL_FLOAT32_SCHEMA)
        .field("double", Schema.OPTIONAL_FLOAT64_SCHEMA)
        .build();

    final Struct struct1 = new Struct(schema)
        .put("firstName", "Alex")
        .put("lastName", "Smith")
        .put("bool", true)
        .put("short", (short) 1234)
        .put("byte", (byte) -32)
        .put("long", 12425436L)
        .put("float", (float) 2356.3)
        .put("double", -2436546.56457)
        .put("age", 21);

    final Struct struct2 = new Struct(schema)
        .put("firstName", "Christina")
        .put("lastName", "Brams")
        .put("bool", false)
        .put("byte", (byte) -72)
        .put("long", 8594L)
        .put("double", 3256677.56457d)
        .put("age", 28);

    task.put(Arrays.asList(
        new SinkRecord(tableName1, 1, null, null, schema, struct1, 42),
        new SinkRecord(tableName2, 1, null, null, schema, struct2, 43)
    ));

    Thread.sleep(300); //SQLite delay

    assertEquals(
        1,
        sqliteHelper.select(
            "SELECT * FROM " + tableName1,
            new SqliteHelper.ResultSetReadCallback() {
              @Override
              public void read(ResultSet rs) throws SQLException {
                assertEquals(tableName1, rs.getString("kafka_topic"));
                assertEquals(1, rs.getInt("kafka_partition"));
                assertEquals(42, rs.getLong("kafka_offset"));
                assertEquals(struct1.getString("firstName"), rs.getString("firstName"));
                assertEquals(struct1.getString("lastName"), rs.getString("lastName"));
                assertEquals(struct1.getBoolean("bool"), rs.getBoolean("bool"));
                assertEquals(struct1.getInt8("byte").byteValue(), rs.getByte("byte"));
                assertEquals(struct1.getInt16("short").shortValue(), rs.getShort("short"));
                assertEquals(struct1.getInt32("age").intValue(), rs.getInt("age"));
                assertEquals(struct1.getInt64("long").longValue(), rs.getLong("long"));
                assertEquals(struct1.getFloat32("float"), rs.getFloat("float"), 0.01);
                assertEquals(struct1.getFloat64("double"), rs.getDouble("double"), 0.01);
              }
            }
        )
    );

    assertEquals(
        1,
        sqliteHelper.select(
            "SELECT * FROM " + tableName2 + " WHERE firstName='" + struct2.getString("firstName") + "' and lastName='" + struct2.getString("lastName") + "'",
            new SqliteHelper.ResultSetReadCallback() {
              @Override
              public void read(ResultSet rs) throws SQLException {
                assertEquals(struct2.getBoolean("bool"), rs.getBoolean("bool"));
                rs.getShort("short");
                assertTrue(rs.wasNull());
                assertEquals(struct2.getInt8("byte").byteValue(), rs.getByte("byte"));
                assertEquals(struct2.getInt32("age").intValue(), rs.getInt("age"));
                assertEquals(struct2.getInt64("long").longValue(), rs.getLong("long"));
                rs.getShort("float");
                assertTrue(rs.wasNull());
                assertEquals(struct2.getFloat64("double"), rs.getDouble("double"), 0.01);
              }
            }
        )
    );
  }
}
