package org.folio.search.utils;

import static org.folio.search.configuration.properties.FolioEnvironment.getFolioEnvName;

import java.util.concurrent.Callable;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.folio.search.exception.SearchOperationException;
import org.folio.search.model.ResourceRequest;
import org.folio.search.model.SearchResource;
import org.folio.search.model.service.CqlSearchServiceRequest;
import org.folio.search.model.service.ResourceIdEvent;
import org.folio.spring.integration.XOkapiHeaders;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SearchUtils {

  public static final String INSTANCE_RESOURCE = SearchResource.INSTANCE.getName();
  public static final String X_OKAPI_TENANT_HEADER = XOkapiHeaders.TENANT;
  public static final String MULTILANG_SOURCE_SUBFIELD = "src";
  public static final String PLAIN_MULTILANG_PREFIX = "plain_";
  public static final String DOT = ".";

  public static final int MAX_ELASTICSEARCH_QUERY_SIZE = 10_000;

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
      throw new SearchOperationException(String.format(
        "Failed to perform elasticsearch request [index=%s, type=%s, message: %s]",
        index, type, e.getMessage()), e);
    }
  }

  /**
   * Creates index name for given {@link CqlSearchServiceRequest} object.
   *
   * @param request resource name as {@link CqlSearchServiceRequest} object
   * @return generated index name.
   */
  public static String getElasticsearchIndexName(ResourceRequest request) {
    return getElasticsearchIndexName(request.getResource(), request.getTenantId());
  }

  /**
   * Creates index name for passed resource id event.
   *
   * @param event resource event as {@link ResourceIdEvent} object
   * @return generated index name.
   */
  public static String getElasticsearchIndexName(ResourceIdEvent event) {
    return getElasticsearchIndexName(event.getType(), event.getTenant());
  }

  /**
   * Creates index name for given resource name and tenant id.
   *
   * @param resource resource name as {@link String} object
   * @param tenantId tenant id as {@link String} object
   * @return generated index name.
   */
  public static String getElasticsearchIndexName(String resource, String tenantId) {
    return getFolioEnvName().toLowerCase() + "_" + resource + "_" + tenantId;
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

  /**
   * Updates path for multilang field.
   *
   * @param path path to field
   * @return updated path as {@link String}
   */
  public static String updatePathForMultilangField(String path) {
    return path + ".*";
  }

  public static String updatePathForTermQueries(String path) {
    return path.endsWith(".*") ? getPathToPlainMultilangValue(path.substring(0, path.length() - 2)) : path;
  }

  public static String getPathToPlainMultilangValue(String path) {
    var dotIndex = path.lastIndexOf('.');
    return dotIndex < 0
      ? PLAIN_MULTILANG_PREFIX + path
      : path.substring(0, dotIndex) + DOT + PLAIN_MULTILANG_PREFIX + path.substring(dotIndex + 1);
  }
}
