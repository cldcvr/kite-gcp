/*
 * Copyright 2014 joey.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kitesdk.data.spi.hive;

import org.apache.avro.SchemaBuilder;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.metastore.api.Table;
import org.junit.Assert;
import org.junit.Test;
import static org.junit.Assert.*;
import org.kitesdk.data.CompressionType;
import org.kitesdk.data.DatasetDescriptor;

public class TestHiveUtils {

  @Test
  public void testRoundTripDescriptor() throws Exception {
    String namespace = "test_ns";
    String name = "test_table";
    DatasetDescriptor original = new DatasetDescriptor.Builder()
        .schemaUri("resource:schema/user.avsc")
        .location("file:/tmp/data/test_table")
        .build();
    boolean external = true;
    Table table = HiveUtils.tableForDescriptor(namespace, name, original, external);

    Configuration conf = new HiveConf();
    DatasetDescriptor result = HiveUtils.descriptorForTable(conf, table);
    assertEquals(original, result);
  }

  @Test
  public void testUpdateChangesDDL() throws Exception {
    DatasetDescriptor original = new DatasetDescriptor.Builder()
        .schema(SchemaBuilder.record("Test").fields()
            .requiredLong("id")
            .requiredString("data")
            .endRecord())
        .build();
    boolean external = false;
    Table table = HiveUtils.tableForDescriptor("ns", "test", original, external);

    DatasetDescriptor updated = new DatasetDescriptor.Builder()
        .schema(SchemaBuilder.record("Test").fields()
            .requiredLong("id")
            .requiredString("data")
            .nullableString("data2", "")
            .endRecord())
        .build();

    HiveUtils.updateTableSchema(table, updated);

    Assert.assertEquals("Should update the table DDL",
        table.getSd().getCols(),
        HiveSchemaConverter.convertSchema(updated.getSchema()));
  }

  @Test
  public void testRoundTripDescriptorWithCompressionType() throws Exception {
    String namespace = "test_ns";
    String name = "test_table";
    DatasetDescriptor original = new DatasetDescriptor.Builder()
        .schemaUri("resource:schema/user.avsc")
        .location("file:/tmp/data/test_table")
        .compressionType(CompressionType.Deflate)
        .build();
    boolean external = true;
    Table table = HiveUtils.tableForDescriptor(namespace, name, original, external);

    Configuration conf = new HiveConf();
    DatasetDescriptor result = HiveUtils.descriptorForTable(conf, table);
    assertEquals(original, result);
  }

  @Test
  public void testRoundTripDescriptorNoCompressionProperty() throws Exception {
    String namespace = "test_ns";
    String name = "test_table";
    DatasetDescriptor original = new DatasetDescriptor.Builder()
        .schemaUri("resource:schema/user.avsc")
        .location("file:/tmp/data/test_table")
        .build();
    boolean external = true;
    Table table = HiveUtils.tableForDescriptor(namespace, name, original, external);
    assertEquals("snappy", table.getParameters().get("kite.compression.type"));
    table.getParameters().remove("kite.compression.type");

    Configuration conf = new HiveConf();
    DatasetDescriptor result = HiveUtils.descriptorForTable(conf, table);
    assertEquals(original, result);
  }
}
