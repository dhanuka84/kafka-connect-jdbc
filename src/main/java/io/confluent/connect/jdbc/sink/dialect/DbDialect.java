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

package io.confluent.connect.jdbc.sink.dialect;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.errors.ConnectException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.confluent.connect.jdbc.sink.metadata.SinkRecordField;

import static io.confluent.connect.jdbc.sink.dialect.StringBuilderUtil.Transform;
import static io.confluent.connect.jdbc.sink.dialect.StringBuilderUtil.joinToBuilder;
import static io.confluent.connect.jdbc.sink.dialect.StringBuilderUtil.nCopiesToBuilder;
import static io.confluent.connect.jdbc.sink.dialect.StringBuilderUtil.stringSurroundTransform;

public abstract class DbDialect {

  private final Map<Schema.Type, String> schemaTypeToSqlTypeMap;
  protected final String escapeColumnNamesStart;
  protected final String escapeColumnNamesEnd;

  DbDialect(Map<Schema.Type, String> schemaTypeToSqlTypeMap, String escapeColumnNamesStart, String escapeColumnNamesEnd) {
    this.schemaTypeToSqlTypeMap = schemaTypeToSqlTypeMap;
    this.escapeColumnNamesStart = escapeColumnNamesStart;
    this.escapeColumnNamesEnd = escapeColumnNamesEnd;
  }

  public final String getInsert(final String tableName, final Collection<String> keyColumns, final Collection<String> nonKeyColumns) {
    StringBuilder builder = new StringBuilder("INSERT INTO ");
    builder.append(escapeTableName(tableName));
    builder.append("(");
    joinToBuilder(builder, ",", keyColumns, nonKeyColumns, stringSurroundTransform(escapeColumnNamesStart, escapeColumnNamesEnd));
    builder.append(") VALUES(");
    nCopiesToBuilder(builder, ",", "?", keyColumns.size() + nonKeyColumns.size());
    builder.append(")");
    return builder.toString();
  }

  public abstract String getUpsertQuery(final String table, final Collection<String> keyColumns, final Collection<String> columns);

  public String getCreateQuery(String tableName, Collection<SinkRecordField> fields) {
    final List<String> pkFieldNames = extractPrimaryKeyFieldNames(fields);
    final StringBuilder builder = new StringBuilder();
    builder.append("CREATE TABLE ");
    builder.append(escapeTableName(tableName));
    builder.append(" (");
    writeColumnsSpec(builder, fields);
    if (!pkFieldNames.isEmpty()) {
      builder.append(",");
      builder.append(System.lineSeparator());
      builder.append("PRIMARY KEY(");
      joinToBuilder(builder, ",", pkFieldNames, stringSurroundTransform(escapeColumnNamesStart, escapeColumnNamesEnd));
      builder.append(")");
    }
    builder.append(")");
    return builder.toString();
  }

  public List<String> getAlterTable(String tableName, Collection<SinkRecordField> fields) {
    final boolean newlines = fields.size() > 1;

    final StringBuilder builder = new StringBuilder("ALTER TABLE ");
    builder.append(escapeTableName(tableName));
    builder.append(" ");
    joinToBuilder(builder, ",", fields, new Transform<SinkRecordField>() {
      @Override
      public void apply(StringBuilder builder, SinkRecordField f) {
        if (newlines) {
          builder.append(System.lineSeparator());
        }
        builder.append("ADD ");
        writeColumnSpec(builder, f);
      }
    });
    return Collections.singletonList(builder.toString());
  }

  protected void writeColumnsSpec(StringBuilder builder, Collection<SinkRecordField> fields) {
    joinToBuilder(builder, ",", fields, new Transform<SinkRecordField>() {
      @Override
      public void apply(StringBuilder builder, SinkRecordField f) {
        builder.append(System.lineSeparator());
        writeColumnSpec(builder, f);
      }
    });
  }

  protected void writeColumnSpec(StringBuilder builder, SinkRecordField f) {
    builder.append(escapeColumnNamesStart).append(f.name).append(escapeColumnNamesEnd);
    builder.append(" ");
    builder.append(getSqlType(f.type));
    if (f.isOptional) {
      builder.append(" NULL");
    } else {
      builder.append(" NOT NULL");
    }
  }

  protected String getSqlType(Schema.Type type) {
    final String sqlType = schemaTypeToSqlTypeMap.get(type);
    if (sqlType == null) {
      throw new ConnectException(String.format("%s type doesn't have a mapping to the SQL database column type", type));
    }
    return sqlType;
  }

  protected String escapeTableName(String tableName) {
    return escapeColumnNamesStart + tableName + escapeColumnNamesEnd;
  }

  static List<String> extractPrimaryKeyFieldNames(Collection<SinkRecordField> fields) {
    final List<String> pks = new ArrayList<>();
    for (SinkRecordField f : fields) {
      if (f.isPrimaryKey) {
        pks.add(f.name);
      }
    }
    return pks;
  }

  public static DbDialect fromConnectionString(final String url) {
    if (!url.startsWith("jdbc:")) {
      throw new ConnectException(String.format("Not a valid JDBC URL: %s", url));
    }

    if (url.startsWith("jdbc:sqlite:")) {
      // SQLite URL's are not in the format jdbc:protocol://FILE but jdbc:protocol:file
      return new SqliteDialect();
    }

    if (url.startsWith("jdbc:oracle:thin:@")) {
      return new OracleDialect();
    }

    final String protocol = extractProtocolFromUrl(url).toLowerCase();
    switch (protocol) {
      case "microsoft:sqlserver":
      case "sqlserver":
      case "jtds:sqlserver":
        return new SqlServerDialect();
      case "mariadb":
      case "mysql":
        return new MySqlDialect();
      case "postgresql":
        return new PostgreSqlDialect();
      default:
        return new GenericDialect();
    }
  }

  static String extractProtocolFromUrl(final String url) {
    if (!url.startsWith("jdbc:")) {
      throw new ConnectException(String.format("Not a valid JDBC URL: %s", url));
    }
    final int index = url.indexOf("://", "jdbc:".length());
    if (index < 0) {
      throw new ConnectException(String.format("Not a valid JDBC URL: %s", url));
    }
    return url.substring("jdbc:".length(), index);
  }
}