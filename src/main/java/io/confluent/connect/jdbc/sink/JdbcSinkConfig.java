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

import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigException;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.confluent.connect.jdbc.sink.metadata.FieldsMetadata;
import io.confluent.connect.jdbc.util.StringUtils;

public class JdbcSinkConfig extends AbstractConfig {

  public enum InsertMode {
    INSERT,
    UPSERT;
  }

  public enum PrimaryKeyMode {
    NONE,
    KAFKA,
    RECORD_KEY,
    RECORD_VALUE;
  }

  private static final String TOPIC_OVERRIDABLE_DOC = "This config is overridable at the topic-level by using a ``$topic.`` prefix.";
  private static final String TABLE_OVERRIDABLE_DOC = "This config is overridable at the table-level by using a ``$table.`` prefix.";

  public static final String CONNECTION_URL = "connection.url";
  private static final String CONNECTION_URL_DOC =
      "JDBC connection URL.\n"
      + "The protocol portion may be used for specializing the SQL dialect to be used.";
  private static final String CONNECTION_URL_DISPLAY = "JDBC URL";

  public static final String CONNECTION_USER = "connection.user";
  private static final String CONNECTION_USER_DOC = "JDBC connection user.";
  private static final String CONNECTION_USER_DISPLAY = "JDBC User";

  public static final String CONNECTION_PASSWORD = "connection.password";
  private static final String CONNECTION_PASSWORD_DOC = "JDBC connection password.";
  private static final String CONNECTION_PASSWORD_DISPLAY = "JDBC Password";

  public static final String TABLE_NAME_FORMAT = "table.name.format";
  private static final String TABLE_NAME_FORMAT_DEFAULT = "${topic}";
  private static final String TABLE_NAME_FORMAT_DOC =
      "A format string for the destination table name, which may contain '${topic}' as a placeholder for the originating topic name.\n"
      + "For example, ``kafka_${topic}`` for the topic 'orders' will map to the table name 'kafka_orders'.\n"
      + TOPIC_OVERRIDABLE_DOC;
  private static final String TABLE_NAME_FORMAT_DISPLAY = "Table Name Format";

  public static final String MAX_RETRIES = "max.retries";
  private static final int MAX_RETRIES_DEFAULT = 10;
  private static final String MAX_RETRIES_DOC =
      "The maximum number of times to retry on errors before failing the task.";
  private static final String MAX_RETRIES_DISPLAY = "Maximum Retries";

  public static final String RETRY_BACKOFF_MS = "retry.backoff.ms";
  private static final int RETRY_BACKOFF_MS_DEFAULT = 3000;
  private static final String RETRY_BACKOFF_MS_DOC =
      "The time in milliseconds to wait following an error before a retry attempt is made.";
  private static final String RETRY_BACKOFF_MS_DISPLAY = "Retry Backoff (millis)";

  public static final String BATCH_SIZE = "batch.size";
  private static final int BATCH_SIZE_DEFAULT = 3000;
  private static final String BATCH_SIZE_DOC =
      "Specifies how many records to attempt to batch together for insertion, when possible.\n"
      + TABLE_OVERRIDABLE_DOC;
  private static final String BATCH_SIZE_DISPLAY = "Batch Size";

  public static final String AUTO_CREATE = "auto.create";
  private static final String AUTO_CREATE_DEFAULT = "false";
  private static final String AUTO_CREATE_DOC =
      "Whether to automatically create tables based on record schema if the sink table is found to be missing, by issuing a ``CREATE`` statement.\n"
      + "Each column is assigned an appropriate data type using standard SQL data types based on the record schema, with some database-specific overrides.\n"
      + "Primary keys are specified as per the relevant key configuration settings, and nullability of non-primary-key fields is based on their optionality in the schema.\n"
      + TABLE_OVERRIDABLE_DOC;
  private static final String AUTO_CREATE_DISPLAY = "Auto-create";

  public static final String AUTO_EVOLVE = "auto.evolve";
  private static final String AUTO_EVOLVE_DEFAULT = "false";
  private static final String AUTO_EVOLVE_DOC =
      "Whether to automatically evolve table schema when record schema and table schema is found to be incompatible, by issuing an ``ALTER`` statement.\n"
      + "Only columns that are missing in the destination table can be detected for adding with the auto-evolution feature.\n"
      + TABLE_OVERRIDABLE_DOC;
  private static final String AUTO_EVOLVE_DISPLAY = "Auto-evolve";

  public static final String INSERT_MODE = "insert.mode";
  private static final String INSERT_MODE_DEFAULT = "insert";
  private static final String INSERT_MODE_DOC =
      "The insertion mode to use. Supported modes are:\n"
      + "`insert`\n"
      + "    Use standard SQL ``INSERT`` statements.\n"
      + "`upsert`\n"
      + "    Use the appropriate upsert semantics for the target database if it is supported by the connector, e.g. ``INSERT OR IGNORE``.\n"
      + TABLE_OVERRIDABLE_DOC;
  private static final String INSERT_MODE_DISPLAY = "Insert Mode";

  public static final String PK_MODE = "pk.mode";
  private static final String PK_MODE_DEFAULT = "none";
  private static final String PK_MODE_DOC =
      "The primary key mode, also refer to ``pk.fields`` documentation for interplay. Supported modes are:\n"
      + "`none`\n"
      + "    No keys utilized.\n"
      + "`kafka`\n"
      + "    Kafka coordinates are used as the PK.\n"
      + "`record_key`\n"
      + "    Field(s) from the record key are used, which may be a primitive or a struct.\n"
      + "`record_value`\n"
      + "    Field(s) from the record value are used, which must be a struct.\n";
  private static final String PK_MODE_DISPLAY = "Primary Key Mode";

  public static final String PK_FIELDS = "pk.fields";
  private static final String PK_FIELDS_DEFAULT = "";
  private static final String PK_FIELDS_DOC =
      "List of comma-separated primary key field names. The runtime interpretation of this config depends on the ``pk.mode``:"
      + "`none`\n"
      + "    Ignored as no fields are used as primary key in this mode.\n"
      + "`kafka`\n"
      + "    Must be a trio representing the Kafka coordinates, defaults to ``" + StringUtils.join(FieldsMetadata.DEFAULT_KAFKA_PK_NAMES, ",") + "`` if empty.\n"
      + "`record_key`\n"
      + "    If empty, all fields from the key struct will be used, otherwise used to whitelist the desired fields - for primitive key only a single field name must be configured.\n"
      + "`record_value`\n"
      + "    If empty, all fields from the value struct will be used, otherwise used to whitelist the desired fields.\n";
  private static final String PK_FIELDS_DISPLAY = "Primary Key Fields";

  private static final ConfigDef.Range NON_NEGATIVE_INT_VALIDATOR = ConfigDef.Range.atLeast(0);

  // (String name, ConfigDef.Type type, Object defaultValue, ConfigDef.Importance importance, String documentation, String group, int orderInGroup, ConfigDef.Width width, String displayName) {

  private static final String DATABASE_GROUP = "Database";
  private static final String CONNECTOR_GROUP = "Connector";
  private static final String MODE_GROUP = "Mode";

  public static final ConfigDef CONFIG_DEF = new ConfigDef()
      // Database
      .define(CONNECTION_URL, ConfigDef.Type.STRING, ConfigDef.NO_DEFAULT_VALUE,
              ConfigDef.Importance.HIGH, CONNECTION_URL_DOC,
              DATABASE_GROUP, 1, ConfigDef.Width.LONG, CONNECTION_URL_DISPLAY)
      .define(CONNECTION_USER, ConfigDef.Type.STRING, null,
              ConfigDef.Importance.HIGH, CONNECTION_USER_DOC,
              DATABASE_GROUP, 2, ConfigDef.Width.MEDIUM, CONNECTION_USER_DISPLAY)
      .define(CONNECTION_PASSWORD, ConfigDef.Type.PASSWORD, null,
              ConfigDef.Importance.HIGH, CONNECTION_PASSWORD_DOC,
              DATABASE_GROUP, 3, ConfigDef.Width.MEDIUM, CONNECTION_PASSWORD_DISPLAY)
      // Connector
      .define(TABLE_NAME_FORMAT, ConfigDef.Type.STRING, TABLE_NAME_FORMAT_DEFAULT,
              ConfigDef.Importance.MEDIUM, TABLE_NAME_FORMAT_DOC,
              CONNECTOR_GROUP, 1, ConfigDef.Width.LONG, TABLE_NAME_FORMAT_DISPLAY)
      .define(BATCH_SIZE, ConfigDef.Type.INT, BATCH_SIZE_DEFAULT, NON_NEGATIVE_INT_VALIDATOR,
              ConfigDef.Importance.HIGH, BATCH_SIZE_DOC,
              CONNECTOR_GROUP, 2, ConfigDef.Width.SHORT, BATCH_SIZE_DISPLAY)
      .define(MAX_RETRIES, ConfigDef.Type.INT, MAX_RETRIES_DEFAULT, NON_NEGATIVE_INT_VALIDATOR,
              ConfigDef.Importance.MEDIUM, MAX_RETRIES_DOC,
              CONNECTOR_GROUP, 3, ConfigDef.Width.SHORT, MAX_RETRIES_DISPLAY)
      .define(RETRY_BACKOFF_MS, ConfigDef.Type.INT, RETRY_BACKOFF_MS_DEFAULT, NON_NEGATIVE_INT_VALIDATOR,
              ConfigDef.Importance.MEDIUM, RETRY_BACKOFF_MS_DOC,
              CONNECTOR_GROUP, 4, ConfigDef.Width.SHORT, RETRY_BACKOFF_MS_DISPLAY)
      // Mode
      .define(AUTO_CREATE, ConfigDef.Type.BOOLEAN, AUTO_CREATE_DEFAULT,
              ConfigDef.Importance.MEDIUM, AUTO_CREATE_DOC,
              MODE_GROUP, 1, ConfigDef.Width.SHORT, AUTO_CREATE_DISPLAY)
      .define(AUTO_EVOLVE, ConfigDef.Type.BOOLEAN, AUTO_EVOLVE_DEFAULT,
              ConfigDef.Importance.MEDIUM, AUTO_EVOLVE_DOC,
              MODE_GROUP, 2, ConfigDef.Width.SHORT, AUTO_EVOLVE_DISPLAY)
      .define(INSERT_MODE, ConfigDef.Type.STRING, INSERT_MODE_DEFAULT, EnumValidator.in(InsertMode.values()),
              ConfigDef.Importance.MEDIUM, INSERT_MODE_DOC,
              MODE_GROUP, 3, ConfigDef.Width.MEDIUM, INSERT_MODE_DISPLAY)
      .define(PK_MODE, ConfigDef.Type.STRING, PK_MODE_DEFAULT, EnumValidator.in(PrimaryKeyMode.values()),
              ConfigDef.Importance.HIGH, PK_MODE_DOC,
              MODE_GROUP, 4, ConfigDef.Width.MEDIUM, PK_MODE_DISPLAY)
      .define(PK_FIELDS, ConfigDef.Type.LIST, PK_FIELDS_DEFAULT,
              ConfigDef.Importance.MEDIUM, PK_FIELDS_DOC,
              MODE_GROUP, 5, ConfigDef.Width.LONG, PK_FIELDS_DISPLAY);

  public final String connectionUrl;
  public final String connectionUser;
  public final String connectionPassword;
  public final String tableNameFormat;
  public final int batchSize;
  public final int maxRetries;
  public final int retryBackoffMs;
  public final boolean autoCreate;
  public final boolean autoEvolve;
  public final InsertMode insertMode;
  public final PrimaryKeyMode pkMode;
  public final List<String> pkFields;

  public JdbcSinkConfig(Map<?, ?> props) {
    super(CONFIG_DEF, props);
    connectionUrl = getString(CONNECTION_URL);
    connectionUser = getString(CONNECTION_USER);
    connectionPassword = getString(CONNECTION_PASSWORD);
    tableNameFormat = getString(TABLE_NAME_FORMAT);
    batchSize = getInt(BATCH_SIZE);
    maxRetries = getInt(MAX_RETRIES);
    retryBackoffMs = getInt(RETRY_BACKOFF_MS);
    autoCreate = getBoolean(AUTO_CREATE);
    autoEvolve = getBoolean(AUTO_EVOLVE);
    insertMode = InsertMode.valueOf(getString(INSERT_MODE).toUpperCase());
    pkMode = PrimaryKeyMode.valueOf(getString(PK_MODE).toUpperCase());
    pkFields = getList(PK_FIELDS);
  }

  public JdbcSinkConfig contextualConfig(String context) {
    final Map<String, Object> properties = originals();
    properties.putAll(originalsWithPrefix(context + "."));
    return new JdbcSinkConfig(properties);
  }

  private static class EnumValidator implements ConfigDef.Validator {
    private final Set<String> validValues;

    private EnumValidator(Set<String> validValues) {
      this.validValues = validValues;
    }

    public static <E> EnumValidator in(E[] enumerators) {
      final HashSet<String> values = new HashSet<>();
      for (E e : enumerators) {
        values.add(e.toString().toUpperCase());
        values.add(e.toString().toLowerCase());
      }
      return new EnumValidator(values);
    }

    @Override
    public void ensureValid(String key, Object value) {
      if (!validValues.contains(value)) {
        throw new ConfigException(key, value, "Invalid enumerator");
      }
    }
  }

  public static void main(String... args) {
    System.out.println(CONFIG_DEF.toRst());
  }

}