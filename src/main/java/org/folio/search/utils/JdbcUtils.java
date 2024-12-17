package org.folio.search.utils;

import static java.util.Collections.nCopies;

import lombok.experimental.UtilityClass;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;

@UtilityClass
public class JdbcUtils {

  private static final String TRUNCATE_TABLE_SQL = "TRUNCATE TABLE %s;";

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
}
