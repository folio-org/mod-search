package org.folio.search.utils;

import static java.util.Collections.nCopies;

import lombok.experimental.UtilityClass;
import org.folio.spring.FolioExecutionContext;

@UtilityClass
public class JdbcUtils {

  public static String getFullTableName(FolioExecutionContext context, String tableName) {
    var dbSchemaName = context.getFolioModuleMetadata().getDBSchemaName(context.getTenantId());
    return dbSchemaName + "." + tableName;
  }

  public static String getParamPlaceholderForUuid(int size) {
    return getParamPlaceholder(size, "uuid");
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
}
