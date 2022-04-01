package org.folio.search.utils;

import static java.util.Locale.ROOT;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static org.folio.search.configuration.properties.FolioEnvironment.getFolioEnvName;
import static org.folio.search.model.metadata.PlainFieldDescription.STANDARD_FIELD_TYPE;
import static org.folio.search.utils.CollectionUtils.mergeSafelyToSet;

import com.google.common.base.CaseFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation.Bucket;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedTerms;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.Contributor;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.exception.SearchOperationException;
import org.folio.search.model.ResourceRequest;
import org.folio.search.model.index.SearchDocumentBody;
import org.folio.search.model.metadata.PlainFieldDescription;
import org.folio.search.model.service.MultilangValue;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SearchUtils {

  public static final String INSTANCE_RESOURCE = getResourceName(Instance.class);
  public static final String INSTANCE_SUBJECT_RESOURCE = "instance_subject";
  public static final String AUTHORITY_RESOURCE = getResourceName(Authority.class);
  public static final String CONTRIBUTOR_RESOURCE = getResourceName(Contributor.class);

  public static final String ID_FIELD = "id";
  public static final String INSTANCE_ITEM_FIELD_NAME = "items";
  public static final String INSTANCE_HOLDING_FIELD_NAME = "holdings";
  public static final String IS_BOUND_WITH_FIELD_NAME = "isBoundWith";
  public static final String CALL_NUMBER_BROWSING_FIELD = "itemEffectiveShelvingOrder";
  public static final String SUBJECT_BROWSING_FIELD = "subject";
  public static final String AUTHORITY_BROWSING_FIELD = "headingRef";
  public static final String SUBJECT_AGGREGATION_NAME = "subjects";
  public static final String ITEM_SHELF_KEY_FIELD_NAME =
    CALL_NUMBER_BROWSING_FIELD.substring(INSTANCE_ITEM_FIELD_NAME.length() + 1);

  public static final String CQL_META_FIELD_PREFIX = "cql.";
  public static final String MULTILANG_SOURCE_SUBFIELD = "src";
  public static final String PLAIN_FULLTEXT_PREFIX = "plain_";
  public static final String SELECTED_AGG_PREFIX = "selected_";
  public static final String ASTERISKS_SIGN = "*";
  public static final String DOT = ".";
  public static final String EMPTY_ARRAY = "[]";
  public static final String KEYWORD_FIELD_INDEX = "keyword";
  public static final float CONST_SIZE_LOAD_FACTOR = 1.0f;

  /**
   * Performs elasticsearch exceptional operation and returns the received result.
   *
   * @param func exceptional operation as {@link Callable} lambda.
   * @param index elasticsearch index for error message.
   * @param type operation type for error message.
   * @throws SearchOperationException if function call throws an exception during execution.
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
   * Creates index name for given {@link ResourceRequest} object.
   *
   * @param request - request as {@link ResourceRequest} object
   * @return generated index name.
   */
  public static String getIndexName(ResourceRequest request) {
    return getIndexName(request.getResource(), request.getTenantId());
  }

  /**
   * Creates index name for passed resource id event.
   *
   * @param event resource event as {@link ResourceEvent} object
   * @return generated index name.
   */
  public static String getIndexName(ResourceEvent event) {
    return getIndexName(event.getResourceName(), event.getTenant());
  }

  /**
   * Creates index name for given resource name and tenant id.
   *
   * @param resource resource name as {@link String} object
   * @param tenantId tenant id as {@link String} object
   * @return generated index name.
   */
  public static String getIndexName(String resource, String tenantId) {
    return getFolioEnvName().toLowerCase(ROOT) + "_" + resource + "_" + tenantId;
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
    return description.isMultilang() ? getPathForMultilangField(path) : path;
  }

  /**
   * Updates path for multilang field.
   *
   * @param path path to field
   * @return updated path as {@link String} object
   */
  public static String getPathForMultilangField(String path) {
    return path + ".*";
  }

  /**
   * Updates path for field at term-level queries.
   *
   * @param path path to field
   * @return updated path as {@link String} object
   */
  public static String updatePathForTermQueries(String path) {
    return path.endsWith(".*") ? getPathToFulltextPlainValue(path.substring(0, path.length() - 2)) : path;
  }

  /**
   * Returns path to plain multilang value using given path.
   *
   * @param path - path to analyze and update as {@link String} object.
   * @return plain path to the multilang value.
   */
  public static String getPathToFulltextPlainValue(String path) {
    var dotIndex = path.lastIndexOf('.');
    return dotIndex < 0
      ? PLAIN_FULLTEXT_PREFIX + path
      : path.substring(0, dotIndex) + DOT + PLAIN_FULLTEXT_PREFIX + path.substring(dotIndex + 1);
  }

  /**
   * Checks if path relates to multilang search.
   *
   * @param path path to field
   * @return true if path ends with multilang suffix, false - otherwise
   */
  public static boolean isMultilangFieldPath(String path) {
    return path != null && path.endsWith(".*");
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
   * Creates normalized call number for passed call number parts (prefix, call number and suffix).
   *
   * @param callNumberValues array with full call number parts (prefix, call number and suffix)
   * @return created normalized call number as {@link String} value
   */
  public static String getNormalizedCallNumber(String... callNumberValues) {
    return Stream.of(callNumberValues)
      .filter(StringUtils::isNotBlank)
      .map(s -> s.toLowerCase().replaceAll("[^a-z0-9]", ""))
      .collect(Collectors.joining(""));
  }

  /**
   * Returns plain field value for fulltext value.
   *
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
    var multilangValue = getMultilangValueObject(value);

    languages.forEach(language -> multilangValueMap.put(language, multilangValue));
    multilangValueMap.put(MULTILANG_SOURCE_SUBFIELD, multilangValue);

    var resultMap = new LinkedHashMap<String, Object>(2, CONST_SIZE_LOAD_FACTOR);
    resultMap.put(key, multilangValueMap);
    resultMap.put(PLAIN_FULLTEXT_PREFIX + key, getPlainValueObject(value));

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
    fulltextValue.put(PLAIN_FULLTEXT_PREFIX + key, value);

    return fulltextValue;
  }

  /**
   * Removes 'plain_' prefix from key if it's starting from it, returns given key otherwise.
   *
   * @param key field name to analyze
   * @return key without '_plain' prefix if it's starting from it.
   */
  public static String updateMultilangPlainFieldKey(String key) {
    return key.startsWith(PLAIN_FULLTEXT_PREFIX) ? key.substring(PLAIN_FULLTEXT_PREFIX.length()) : key;
  }

  /**
   * Provides resource name for given resource class.
   *
   * @param resourceClass - resource class to extract resource name from
   * @return resource class as {@link String} object
   */
  public static String getResourceName(Class<?> resourceClass) {
    return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, resourceClass.getSimpleName());
  }

  /**
   * Check if passed value is {@link String} and it's empty.
   *
   * @param value - value to check
   * @return true - if value is empty, false - otherwise
   */
  public static boolean isEmptyString(Object value) {
    return value instanceof String && ((String) value).isEmpty();
  }

  /**
   * Get subject count from count search request.
   *
   * @param searchResponse - search response as {@link SearchResponse} object.
   * @return map with key as the subject name, value as the related subject count.
   */
  public static Map<String, Long> getSubjectCounts(SearchResponse searchResponse) {
    return Optional.ofNullable(searchResponse)
      .map(SearchResponse::getAggregations)
      .map(aggregations -> aggregations.get(SUBJECT_AGGREGATION_NAME))
      .filter(ParsedTerms.class::isInstance)
      .map(ParsedTerms.class::cast)
      .map(ParsedTerms::getBuckets)
      .stream()
      .flatMap(Collection::stream)
      .collect(toMap(Bucket::getKeyAsString, Bucket::getDocCount));
  }

  /**
   * Returns number of requests in map as integer value.
   *
   * @param requestsPerResource - map with requests per resource name
   * @return number of requests as integer value
   */
  public static int getNumberOfRequests(Map<String, List<SearchDocumentBody>> requestsPerResource) {
    if (MapUtils.isEmpty(requestsPerResource)) {
      return 0;
    }

    return requestsPerResource.values().stream()
      .filter(Objects::nonNull)
      .mapToInt(List::size)
      .sum();
  }

  private static Object getMultilangValueObject(Object value) {
    return value instanceof MultilangValue ? ((MultilangValue) value).getMultilangValues() : value;
  }

  private static Object getPlainValueObject(Object value) {
    if (value instanceof MultilangValue) {
      var multilangValue = (MultilangValue) value;
      return mergeSafelyToSet(multilangValue.getMultilangValues(), multilangValue.getPlainValues());
    }
    return value;
  }
}
