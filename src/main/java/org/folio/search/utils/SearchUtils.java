package org.folio.search.utils;

import static java.util.Locale.ROOT;
import static org.folio.search.utils.CollectionUtils.mergeSafelyToSet;
import static org.folio.spring.config.properties.FolioEnvironment.getFolioEnvName;

import com.google.common.base.CaseFormat;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.Contributor;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ShelvingOrderAlgorithmType;
import org.folio.search.exception.SearchOperationException;
import org.folio.search.model.ResourceRequest;
import org.folio.search.model.index.SearchDocumentBody;
import org.folio.search.model.metadata.PlainFieldDescription;
import org.folio.search.model.service.MultilangValue;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SearchUtils {

  public static final String INSTANCE_RESOURCE = getResourceName(Instance.class);
  public static final String INSTANCE_SUBJECT_RESOURCE = "instance_subject";
  public static final String INSTANCE_CLASSIFICATION_RESOURCE = "instance_classification";
  public static final String AUTHORITY_RESOURCE = getResourceName(Authority.class);
  public static final String CONTRIBUTOR_RESOURCE = getResourceName(Contributor.class);
  public static final String LOCATION_RESOURCE = "location";
  public static final String CLASSIFICATION_TYPE_RESOURCE = "classification-type";

  public static final String ID_FIELD = "id";
  public static final String SOURCE_FIELD = "source";
  public static final String INSTANCE_ID_FIELD = "instanceId";
  public static final String INSTANCE_ITEM_FIELD_NAME = "items";
  public static final String INSTANCE_HOLDING_FIELD_NAME = "holdings";
  public static final String INSTANCE_CONTRIBUTORS_FIELD_NAME = "contributors";
  public static final String SHARED_FIELD_NAME = "shared";
  public static final String TENANT_ID_FIELD_NAME = "tenantId";
  public static final String IS_BOUND_WITH_FIELD_NAME = "isBoundWith";
  public static final String CALL_NUMBER_BROWSING_FIELD = "callNumber";
  public static final String CLASSIFICATION_NUMBER_BROWSING_FIELD = "number";
  public static final String CLASSIFICATION_TYPE_ID_FIELD = "typeId";
  public static final String TYPED_CALL_NUMBER_BROWSING_FIELD = "typedCallNumber";
  public static final String SHELVING_ORDER_BROWSING_FIELD = "itemEffectiveShelvingOrder";
  public static final String DEFAULT_SHELVING_ORDER_BROWSING_FIELD = "defaultShelvingOrder";
  public static final String LC_SHELVING_ORDER_BROWSING_FIELD = "lcShelvingOrder";
  public static final String DEWEY_SHELVING_ORDER_BROWSING_FIELD = "deweyShelvingOrder";
  public static final String SUBJECT_BROWSING_FIELD = "value";
  public static final String CONTRIBUTOR_BROWSING_FIELD = "name";
  public static final String AUTHORITY_BROWSING_FIELD = "headingRef";
  public static final String AUTHORITY_ID_FIELD = "authorityId";
  public static final String CLASSIFICATIONS_FIELD = "classifications";
  public static final String CLASSIFICATION_NUMBER_FIELD = "classificationNumber";
  public static final String CLASSIFICATION_TYPE_FIELD = "classificationTypeId";
  public static final String SUBJECT_AGGREGATION_NAME = "subjects.value";
  public static final String SOURCE_CONSORTIUM_PREFIX = "CONSORTIUM-";

  public static final String CQL_META_FIELD_PREFIX = "cql.";
  public static final String MULTILANG_SOURCE_SUBFIELD = "src";
  public static final String PLAIN_FULLTEXT_PREFIX = "plain_";
  public static final String SELECTED_AGG_PREFIX = "selected_";
  public static final String ASTERISKS_SIGN = "*";
  public static final String DOT = ".";
  public static final String EMPTY_ARRAY = "[]";
  public static final String KEYWORD_FIELD_INDEX = "keyword";
  public static final float CONST_SIZE_LOAD_FACTOR = 1.0f;

  public static final Map<ShelvingOrderAlgorithmType, String> BROWSE_FIELDS_MAP = Map.of(
    ShelvingOrderAlgorithmType.DEFAULT, DEFAULT_SHELVING_ORDER_BROWSING_FIELD,
    ShelvingOrderAlgorithmType.LC, LC_SHELVING_ORDER_BROWSING_FIELD,
    ShelvingOrderAlgorithmType.DEWEY, DEWEY_SHELVING_ORDER_BROWSING_FIELD
  );

  //CHECKSTYLE.ON: LineLength
  public static final String INSTANCE_SUBJECT_UPSERT_SCRIPT_ID = "instance_subject_upsert_script";
  public static final String INSTANCE_SUBJECT_UPSERT_SCRIPT = """
    {
      "script": {
        "lang": "painless",
        "source": "def instances=new LinkedHashSet(ctx._source.instances);instances.addAll(params.ins);params.del.forEach(instances::remove);if (instances.isEmpty()) {ctx.op = 'delete'; return;}ctx._source.instances=instances;"
      }
    }
    """;
  public static final String INSTANCE_CONTRIBUTORS_UPSERT_SCRIPT_ID = "instance_contributors_upsert_script";
  public static final String INSTANCE_CONTRIBUTORS_UPSERT_SCRIPT = """
    {
      "script" : {
        "lang" : "painless",
        "source" : "def instances=new LinkedHashSet(ctx._source.instances);instances.addAll(params.ins);params.del.forEach(instances::remove);if (instances.isEmpty()) {ctx.op = 'delete'; return;}ctx._source.instances=instances;"
      }
    }
    """;
  //CHECKSTYLE.OFF: LineLength

  private static final Pattern LCCN_NUMERIC_PART_REGEX = Pattern.compile("([1-9]\\d+)");
  private static final Pattern NON_ALPHA_NUMERIC_CHARS_PATTERN = Pattern.compile("[^a-zA-Z0-9]");

  /**
   * Performs elasticsearch exceptional operation and returns the received result.
   *
   * @param func  exceptional operation as {@link Callable} lambda.
   * @param index elasticsearch index for error message.
   * @param type  operation type for error message.
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
    return getFolioEnvName().toLowerCase(ROOT) + "_" + resource.toLowerCase(ROOT) + "_" + tenantId;
  }

  /**
   * Calculates total pages for given total results and page size.
   *
   * @param total    total hits as long value
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
   * @param path        path to field
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
   * Returns plain field value for fulltext value.
   *
   * @param description plain field description as {@link PlainFieldDescription} object
   * @param fieldName   field name as {@link String} object
   * @param fieldValue  field value as {@link Object} object.
   * @param languages   list of supported languages for multi-language fields
   * @return {@link Map} as a created field
   */
  public static Map<String, Object> getPlainFieldValue(
    PlainFieldDescription description, String fieldName, Object fieldValue, List<String> languages) {
    if (description.isMultilang()) {
      return getMultilangValue(fieldName, fieldValue, languages);
    }
    if (description.hasFulltextIndex()) {
      return getStandardFulltextValue(fieldName, fieldValue, description.isIndexPlainValue());
    }
    return Collections.singletonMap(fieldName, fieldValue);
  }

  /**
   * Generates multi-language field value for passed key, value and conversion context with supported languages.
   *
   * @param key       name of multi-language field as {@link String} object
   * @param value     multi-language field value as {@link Object} object
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
   * @param key             name of multi-language field as {@link String} object
   * @param value           multi-language field value as {@link Object} object
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
    return value instanceof String string && string.isEmpty();
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

  /**
   * Normalizes LCCN value.
   *
   * @param value LCCN value
   * @return normalized LCCN value
   */
  public static String normalizeLccn(String value) {
    if (StringUtils.isBlank(value)) {
      return null;
    }

    return StringUtils.deleteWhitespace(value).toLowerCase();
  }

  /**
   * This method normalize the given string to an alphanumeric string.
   * If the input string is null or blank, this method returns null.
   * Non-alphanumeric characters in the input string are replaced with an empty string.
   * If the resulting string is blank, this method returns null.
   *
   * @param value The String that is to be normalized.
   * @return The normalized alphanumeric string. If the input string or the resulting string is blank, returns null.
   */
  public static String normalizeToAlphaNumeric(String value) {
    return Optional.ofNullable(value)
      .filter(StringUtils::isNotBlank)
      .map(NON_ALPHA_NUMERIC_CHARS_PATTERN::matcher)
      .map(matcher -> matcher.replaceAll(""))
      .filter(StringUtils::isNotBlank)
      .orElse(null);
  }

  /**
   * Extracts numeric part (digits starting with non-zero) of LCCN String value.
   *
   * @param value LCCN string value
   * @return if exists, returns string of numeric part of LCCN value, otherwise returns null
   */
  public static String extractLccnNumericPart(String value) {
    if (StringUtils.isBlank(value)) {
      return null;
    }

    var matcher = LCCN_NUMERIC_PART_REGEX.matcher(value);
    return matcher.find() ? matcher.group(0) : null;
  }

  private static Object getMultilangValueObject(Object value) {
    return value instanceof MultilangValue v ? v.getMultilangValues() : value;
  }

  private static Object getPlainValueObject(Object value) {
    if (value instanceof MultilangValue multilangValue) {
      return mergeSafelyToSet(multilangValue.getMultilangValues(), multilangValue.getPlainValues());
    }
    return value;
  }
}
