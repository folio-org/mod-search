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

  public static String getParamPlaceholder(int size) {
    return String.join(",", nCopies(size, "?"));
  }

  public static String getGroupedParamPlaceholder(int size, int groupSize) {
    return String.join(",", nCopies(size, "(" + getParamPlaceholder(groupSize) + ")"));
  }
}
