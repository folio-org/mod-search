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

  public static String getQuestionMarkPlaceholder(int size) {
    return String.join(",", nCopies(size, "?"));
  }
}
