package org.folio.search.utils;

import static java.util.Collections.nCopies;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import lombok.experimental.UtilityClass;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.springframework.jdbc.core.JdbcTemplate;

@UtilityClass
public class JdbcUtils {

  private static final String TRUNCATE_TABLE_SQL = "TRUNCATE TABLE %s;";
  private static final String UUID_ARRAY_TYPE = UUID.class.getSimpleName();

  public static String getSchemaName(String tenantId, FolioModuleMetadata folioModuleMetadata) {
    return folioModuleMetadata.getDBSchemaName(tenantId);
  }

  public static String getSchemaName(FolioExecutionContext context) {
    return context.getFolioModuleMetadata().getDBSchemaName(context.getTenantId());
  }

  public static String getFullTableName(FolioExecutionContext context, String tableName) {
    return getSchemaName(context) + "." + tableName;
  }

  public static String getParamPlaceholderForUuid(int size) {
    return getParamPlaceholder(size, "uuid");
  }

  public static String getParamPlaceholderForUuidArray(int size, String cast) {
    return String.join(",", nCopies(size, "?" + (cast == null ? "" : "::" + cast + "[]")));
  }

  public static String getParamPlaceholder(int size) {
    return getParamPlaceholder(size, null);
  }

  public static String getParamPlaceholder(int size, String cast) {
    return String.join(",", nCopies(size, "?" + (cast == null ? "" : "::" + cast)));
  }

  public static String getGroupedParamPlaceholder(int size, int groupSize) {
    return String.join(",", nCopies(size, "(" + getParamPlaceholder(groupSize) + ")"));
  }

  public static void truncateTable(String tableName, JdbcTemplate jdbcTemplate, FolioExecutionContext context) {
    String sql = TRUNCATE_TABLE_SQL.formatted(getFullTableName(context, tableName));
    jdbcTemplate.execute(sql);
  }

  /**
   * Creates and returns an SQL array of type UUID with the given list of string values.
   *
   * @param values    a list of string values to be converted into an SQL array
   * @param statement the PreparedStatement object used to retrieve the database connection
   * @return an Array object containing the UUID values created from the provided list
   * @throws SQLException if the creation of the array fails
   */
  public static Array getUuidArrayParam(List<String> values, PreparedStatement statement) throws SQLException {
    return statement.getConnection().createArrayOf(UUID_ARRAY_TYPE, values.toArray());
  }
}
