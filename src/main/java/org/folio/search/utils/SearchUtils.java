package org.folio.search.utils;

import java.util.concurrent.Callable;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.model.service.CqlSearchRequest;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SearchUtils {

  public static final String INSTANCE_RESOURCE = "instance";
  public static final String X_OKAPI_TENANT_HEADER = "X-Okapi-Tenant";
  public static final String MULTILANG_SOURCE_SUBFIELD = "src";

  /**
   * Performs elasticsearch exceptional operation and returns the result if it was positive or throws {@link
   * RuntimeException}.
   *
   * @param func exceptional operation as {@link Callable} lambda.
   * @param index elasticsearch index for error message.
   * @param type operation type for error message.
   */
  public static <T> T performExceptionalOperation(Callable<T> func, String index, String type) {
    try {
      return func.call();
    } catch (Exception e) {
      throw new SearchServiceException(String.format(
        "Failed to perform elasticsearch request [index=%s, type=%s, message: %s]",
        index, type, e.getMessage()), e);
    }
  }

  /**
   * Creates index name for given {@link CqlSearchRequest} object.
   *
   * @param request resource name as {@link CqlSearchRequest} object
   * @return generated index name.
   */
  public static String getElasticsearchIndexName(CqlSearchRequest request) {
    return getElasticsearchIndexName(request.getResource(), request.getTenantId());
  }

  /**
   * Creates index name for given resource name and tenant id.
   *
   * @param resource resource name as {@link String} object
   * @param tenantId tenant id as {@link String} object
   * @return generated index name.
   */
  public static String getElasticsearchIndexName(String resource, String tenantId) {
    return resource + "_" + tenantId;
  }

  /**
   * Calculates total pages for given total results and page size.
   *
   * @param total total hits as long value
   * @param pageSize page size as integer value
   * @return total pages as long value
   */
  public static long getTotalPages(long total, Integer pageSize) {
    return total / pageSize + (total % pageSize != 0 ? 1 : 0);
  }
}
