package org.folio.search.utils;

import static java.util.stream.Collectors.joining;
import static org.folio.search.configuration.properties.FolioEnvironment.getFolioEnvName;
import static org.folio.search.model.metadata.PlainFieldDescription.STANDARD_FIELD_TYPE;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.exception.SearchOperationException;
import org.folio.search.model.ResourceRequest;
import org.folio.search.model.SearchResource;
import org.folio.search.model.metadata.PlainFieldDescription;
import org.folio.search.model.service.CqlSearchServiceRequest;
import org.folio.search.model.service.ResourceIdEvent;
import org.folio.spring.integration.XOkapiHeaders;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SearchUtils {

  public static final String INSTANCE_RESOURCE = SearchResource.INSTANCE.getName();
  public static final String X_OKAPI_TENANT_HEADER = XOkapiHeaders.TENANT;
  public static final String MULTILANG_SOURCE_SUBFIELD = "src";
  public static final String PLAIN_MULTILANG_PREFIX = "plain_";
  public static final String SELECTED_AGG_PREFIX = "selected_";
  public static final String DOT = ".";

  public static final int MAX_ELASTICSEARCH_QUERY_SIZE = 10_000;
  public static final float CONST_SIZE_LOAD_FACTOR = 1.0f;

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
   * Updates path for fulltext field.
   *
   * @param description plain field description as {@link PlainFieldDescription} object
   * @param path path to field
   * @return updated path as {@link String} object
   */
  public static String updatePathForFulltextField(PlainFieldDescription description, String path) {
    return description.isMultilang() ? updatePathForMultilangField(path) : path;
  }

  /**
   * Updates path for multilang field.
   *
   * @param path path to field
   * @return updated path as {@link String} object
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

  /**
   * Creates call number for passed prefix, call number and suffix.
   *
   * @param prefix call number prefix
   * @param callNumber call number value
   * @param suffix call number suffix
   * @return created effective call number as {@link String} value
   */
  public static String getEffectiveCallNumber(String prefix, String callNumber, String suffix) {
    return Stream.of(prefix, callNumber, suffix)
      .filter(StringUtils::isNotBlank)
      .map(String::trim)
      .collect(joining(" "));
  }

  /**
   * Returns plain field value for fulltext value.
   * @param description plain field description as {@link PlainFieldDescription} object
   * @param fieldName field name as {@link String} object
   * @param fieldValue field value as {@link Object} object.
   * @param languages list of supported languages for multi-language fields
   * @return {@link Map} as a created field
   */
  public static Map<String, Object> getPlainFieldValue(
    PlainFieldDescription description, String fieldName, Object fieldValue, List<String> languages) {
    if (description.isMultilang()) {
      return getMultilangValue(fieldName, fieldValue, languages);
    }
    if (STANDARD_FIELD_TYPE.equals(description.getIndex())) {
      return getStandardFulltextValue(fieldName, fieldValue, description.isIndexPlainValue());
    }
    return Collections.singletonMap(fieldName, fieldValue);
  }

  /**
   * Generates multi-language field value for passed key, value and conversion context with supported languages.
   *
   * @param key name of multi-language field as {@link String} object
   * @param value multi-language field value as {@link Object} object
   * @param languages list of languages for multilang field
   * @return created multi-language value as {@link Map}
   */
  public static Map<String, Object> getMultilangValue(String key, Object value, List<String> languages) {
    var multilangValueMap = new LinkedHashMap<String, Object>(languages.size(), CONST_SIZE_LOAD_FACTOR);
    languages.forEach(language -> multilangValueMap.put(language, value));
    multilangValueMap.put(MULTILANG_SOURCE_SUBFIELD, value);

    var resultMap = new LinkedHashMap<String, Object>(2, CONST_SIZE_LOAD_FACTOR);
    resultMap.put(key, multilangValueMap);
    resultMap.put(PLAIN_MULTILANG_PREFIX + key, value);

    return resultMap;
  }

  /**
   * Generates standard fulltext field value for passed key, value and boolean flag for plain field.
   *
   * @param key name of multi-language field as {@link String} object
   * @param value multi-language field value as {@link Object} object
   * @param indexPlainField boolean flag that specifies if plain value must be indexed or not
   * @return created standard fulltext value as {@link Map} object
   */
  public static Map<String, Object> getStandardFulltextValue(String key, Object value, boolean indexPlainField) {
    if (!indexPlainField) {
      return Collections.singletonMap(key, value);
    }
    var fulltextValue = new LinkedHashMap<String, Object>(2, CONST_SIZE_LOAD_FACTOR);
    fulltextValue.put(key, value);
    fulltextValue.put(PLAIN_MULTILANG_PREFIX + key, value);

    return fulltextValue;
  }

  /**
   * Returns nullableList if it is not null or empty, defaultList otherwise.
   *
   * @param nullableList nullable value to check
   * @param <T> generic type for value
   * @return nullableList if it is not null or empty, defaultList otherwise.
   */
  public static <T> Stream<T> toSafeStream(List<T> nullableList) {
    return CollectionUtils.isNotEmpty(nullableList) ? nullableList.stream() : Stream.empty();
  }
}
