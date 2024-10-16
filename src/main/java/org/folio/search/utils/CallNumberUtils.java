package org.folio.search.utils;

import static java.util.Collections.unmodifiableMap;
import static java.util.Locale.ROOT;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.CallNumberBrowseItem;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.model.service.BrowseContext;
import org.folio.search.model.types.CallNumberType;
import org.folio.search.service.consortium.ConsortiumSearchHelper;
import org.jetbrains.annotations.NotNull;
import org.marc4j.callnum.CallNumber;
import org.marc4j.callnum.DeweyCallNumber;
import org.marc4j.callnum.LCCallNumber;
import org.marc4j.callnum.NlmCallNumber;
import org.opensearch.index.query.TermQueryBuilder;
import org.springframework.util.Assert;

@UtilityClass
public class CallNumberUtils {

  private static final int CN_MAX_CHARS = 10;
  private static final char ASCII_SPACE = ' ';
  private static final int MAX_SUPPORTED_CHARACTERS = 52;
  private static final String SUPPORTED_CHARACTERS_STRING = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ .,:;=-+~_/\\#@?!";
  private static final String BROWSE_TENANT_FILTER_KEY = "holdings.tenantId";
  private static final String BROWSE_LOCATION_FILTER_KEY = "items.effectiveLocationId";
  private static final Map<Character, Integer> VALID_CHARACTERS_MAP = getValidCharactersMap();
  private static final Pattern NORMALIZE_REGEX = Pattern.compile("[^a-z0-9]");

  public static String calculateShelvingOrder(Item item) {
    var callNumberComponents = item.getEffectiveCallNumberComponents();
    if (callNumberComponents != null && isNotBlank(callNumberComponents.getCallNumber())) {
      var fullCallNumber = Stream.of(callNumberComponents.getCallNumber(), item.getVolume(), item.getEnumeration(),
            item.getChronology(), item.getCopyNumber(), callNumberComponents.getSuffix())
          .filter(StringUtils::isNotBlank)
          .map(StringUtils::trim)
          .collect(joining(" "));

      return getShelfKeyFromCallNumber(fullCallNumber).orElse(null);
    }

    return null;
  }

  public static Optional<String> getShelfKeyFromCallNumber(String callNumber) {
    return Optional.ofNullable(callNumber)
      .flatMap(cn -> getValidShelfKey(new LCCallNumber(cn))
        .or(() -> getValidShelfKey(new NlmCallNumber(cn)))
        .or(() -> getValidShelfKey(new DeweyCallNumber(cn))))
      .or(() -> Optional.ofNullable(callNumber))
      .map(String::trim)
      .map(val -> val.toUpperCase(ROOT));
  }

  /**
   * Normalizes incoming call-number by removing unsupported characters.
   *
   * @param effectiveShelvingOrder - effective shelving order as {@link String} object
   * @return normalized effective shelving order value
   */
  public static String normalizeEffectiveShelvingOrder(String effectiveShelvingOrder) {
    if (effectiveShelvingOrder == null) {
      return "";
    }

    var string = effectiveShelvingOrder.toUpperCase(ROOT);
    var stringBuilder = new StringBuilder();
    for (int i = 0; i < string.length(); i++) {
      var character = string.charAt(i);
      stringBuilder.append(isSupportedCharacter(character) ? character : ASCII_SPACE);
    }

    return stringBuilder.toString().trim();
  }

  /**
   * Creates normalized call number for passed call number parts (prefix, call number and suffix).
   *
   * @param callNumberValues array with full call number parts (prefix, call number and suffix)
   * @return created normalized call number as {@link String} value
   */
  public static String normalizeCallNumberComponents(String... callNumberValues) {
    return Stream.of(callNumberValues)
      .map(s -> RegExUtils.removeAll(StringUtils.lowerCase(s), NORMALIZE_REGEX))
      .filter(StringUtils::isNotBlank)
      .collect(Collectors.joining(""));
  }

  /**
   * Creates call number for passed prefix, call number and suffix.
   *
   * @param prefix     call number prefix
   * @param callNumber call number value
   * @param suffix     call number suffix
   * @return created effective call number as {@link String} value
   */
  public static String getEffectiveCallNumber(String prefix, String callNumber, String suffix) {
    return Stream.of(prefix, callNumber, suffix)
      .map(StringUtils::trim)
      .filter(StringUtils::isNotBlank)
      .collect(joining(" "));
  }

  /**
   * Checks if character is supported or not.
   *
   * @param character - char value to analyze
   * @return true if character is supported, false - otherwise
   */
  public static boolean isSupportedCharacter(char character) {
    return VALID_CHARACTERS_MAP.containsKey(character);
  }

  /**
   * Provides integer value representation for the given character.
   *
   * @param character    - character to analyze.
   * @param defaultValue - default value if character is not supported
   * @return integer representation for char value
   */
  public static int getIntValue(char character, int defaultValue) {
    return VALID_CHARACTERS_MAP.getOrDefault(character, defaultValue);
  }

  /**
   * Converts incoming call-number into long value.
   *
   * <p>
   * This algorithm takes first 10 character from call-number and converts them into long value using following
   * approach:
   * <ul>
   *   <li>Each character has own unique int value ({@code ' '=0}, {@code '.'=6},
   *   {@code '/'=7}, {@code '0'=8}, {@code '9'-17}, {@code 'A'=23}, {@code 'Z'=48})</li>
   *   <li>each char numeric value multiplied by 52<sup>(10-{charPosition})</sup> (
   *   52 - is the maximum base to not exceed long max value - 2<sup>63</sup>-1)</li>
   *   <li>all received values are summed to the result value</li>
   * </ul>
   * </p>
   *
   * @param callNumber - effective shelving order value from query or from instance item to process
   * @return numeric representation of given call-number value
   */
  public static Long getCallNumberAsLong(String callNumber) {
    return callNumberToLong(callNumber, 0L, CN_MAX_CHARS);
  }

  /**
   * Converts incoming call-number with considering first position into long value.
   *
   * <p>
   * This algorithm takes first 10 character from call-number and converts them into long value using following
   * approach:
   * <ul>
   *   <li>Each character has own unique int value ({@code ' '=0}, {@code '.'=6},
   *   {@code '/'=7}, {@code '0'=8}, {@code '9'-17}, {@code 'A'=23}, {@code 'Z'=48})</li>
   *   <li>each char numeric value multiplied by 52<sup>(10-{charPosition})</sup> (
   *   52 - is the maximum base to not exceed long max value - 2<sup>63</sup>-1)</li>
   *   <li>all received values are summed to the result value</li>
   * </ul>
   * </p>
   *
   * @param callNumber    - effective shelving order value from query or from instance item to process
   * @param firstPosition - int representation for first position
   * @return numeric representation of given call-number value
   */
  public static Long getCallNumberAsLong(String callNumber, int firstPosition) {
    if (firstPosition <= 0) {
      return getCallNumberAsLong(callNumber);
    }
    long startVal = convertChar(firstPosition, CN_MAX_CHARS);
    return callNumberToLong(callNumber, startVal, CN_MAX_CHARS - 1);
  }

  /**
   * Excludes irrelevant items from result.
   *
   * <p>
   * This algorithm takes call number browse result and call number type and removes irrelevant items from result
   * approach:
   * <ul>
   *   <li>Each call number browse item has its own fullCallNumber field, which may vary from its instance's item or
   *   holding call number. Matching ones filtered </li>
   *   <li>CallNumberBrowseItem's instance may have items or holdings
   *   which may have call numbers with different type. They will also be filtered by callNumberType</li>
   *   <li>all filtered records are added together and returned</li>
   * </ul>
   * </p>
   *
   * @param context             - call number browse context
   * @param callNumberTypeValue - call number type to check/compare result items' types
   * @param browseItems         - list of CallNumberBrowseItem objects
   * @return filtered records
   */
  public static List<CallNumberBrowseItem> excludeIrrelevantResultItems(BrowseContext context,
                                                                        String callNumberTypeValue,
                                                                        Set<String> folioCallNumberTypes,
                                                                        List<CallNumberBrowseItem> browseItems) {
    var callNumberType = Optional.ofNullable(StringUtils.trimToNull(callNumberTypeValue));
    var tenantFilter = ConsortiumSearchHelper.getBrowseFilter(context, BROWSE_TENANT_FILTER_KEY);
    var locationFilter = ConsortiumSearchHelper.getBrowseFilterValues(context, BROWSE_LOCATION_FILTER_KEY);
    if (browseItems == null || browseItems.isEmpty()
        || callNumberType.isEmpty() && tenantFilter.isEmpty() && locationFilter.isEmpty()) {
      return browseItems;
    }

    browseItems.forEach(r -> {
      if (r.getInstance() != null) {
        r.getInstance().setItems(
          getItemsFiltered(tenantFilter, locationFilter, callNumberType, folioCallNumberTypes, r));
      }
    });
    return browseItems.stream()
      .filter(CallNumberUtils::isItemRelevant)
      .toList();
  }

  private static Optional<String> getValidShelfKey(CallNumber value) {
    return Optional.of(value)
      .filter(CallNumber::isValid)
      .map(CallNumber::getShelfKey);
  }

  @NotNull
  private static List<@Valid Item> getItemsFiltered(Optional<TermQueryBuilder> tenantFilter,
                                                    List<Object> locationFilter,
                                                    Optional<String> callNumberType,
                                                    Set<String> folioCallNumberTypes,
                                                    CallNumberBrowseItem item) {
    return item.getInstance().getItems()
      .stream()
      .filter(i -> tenantIdMatch(tenantFilter, i) && callNumberTypeMatch(callNumberType, folioCallNumberTypes, i)
                   && locationMatch(locationFilter, i))
      .toList();
  }

  private static boolean isItemRelevant(CallNumberBrowseItem r) {
    Instance instance = r.getInstance();
    if (instance != null) {
      return instance.getItems()
        .stream()
        .anyMatch(i -> {
          String fullCallNumber = getFullCallNumber(i);
          return r.getFullCallNumber() == null
                 || fullCallNumber != null && fullCallNumber.equals(r.getFullCallNumber());
        });
    }
    return true;
  }

  private static boolean callNumberTypeMatch(Optional<String> callNumberType, Set<String> folioCallNumberTypes,
                                             Item item) {
    if (callNumberType.isEmpty()) {
      return true;
    }

    if (item.getEffectiveCallNumberComponents() == null) {
      return false;
    }

    var itemCallNumberTypeId = item.getEffectiveCallNumberComponents().getTypeId();
    var itemCallNumberType = CallNumberType.fromId(itemCallNumberTypeId);
    var requestCallNumberType = CallNumberType.fromName(callNumberType.get());
    return itemCallNumberType.equals(requestCallNumberType)
           || itemCallNumberTypeId != null
              && itemCallNumberType.isEmpty()
              && requestCallNumberType.map(cnt -> cnt.equals(CallNumberType.LOCAL)).orElse(false)
              && !folioCallNumberTypes.contains(itemCallNumberTypeId);
  }

  private static boolean tenantIdMatch(Optional<TermQueryBuilder> tenantFilter, Item item) {
    return tenantFilter.isEmpty() || tenantFilter.get().value().equals(item.getTenantId());
  }

  private static boolean locationMatch(List<Object> locationFilter, Item item) {
    return locationFilter.isEmpty() || locationFilter.stream()
      .anyMatch(location -> location.equals(item.getEffectiveLocationId()));
  }

  private static String getFullCallNumber(Item item) {
    return Optional.ofNullable(item.getEffectiveCallNumberComponents())
      .map(iecnc -> Stream.of(iecnc.getPrefix(), iecnc.getCallNumber(), iecnc.getSuffix())
        .filter(StringUtils::isNotBlank)
        .collect(Collectors.joining(StringUtils.SPACE)))
      .orElse(null);
  }

  private static long callNumberToLong(String callNumber, long startVal, int maxChars) {
    var cleanCallNumber = cleanCallNumber(callNumber, maxChars);
    if (StringUtils.isBlank(cleanCallNumber)) {
      return startVal;
    }
    long result = startVal;
    for (int i = 0; i < cleanCallNumber.length(); i++) {
      var characterValue = getIntValue(cleanCallNumber.charAt(i), 0);
      result += convertChar(characterValue, (double) maxChars - i);
    }
    return result;
  }

  private static String cleanCallNumber(String callNumber, int charsCount) {
    var normalizedCallNumber = normalizeEffectiveShelvingOrder(callNumber);
    return normalizedCallNumber.substring(0, Math.min(charsCount, normalizedCallNumber.length()));
  }

  private static long convertChar(int characterValue, double b) {
    return characterValue * (long) Math.pow(52, b);
  }

  private static Map<Character, Integer> getValidCharactersMap() {
    var supportedCharacters = getSupportedCharactersAsList();
    Assert.isTrue(supportedCharacters.size() <= MAX_SUPPORTED_CHARACTERS, "Number of supported characters is limited.");

    var resultMap = new HashMap<Character, Integer>();
    for (int i = 0; i < supportedCharacters.size(); i++) {
      var key = supportedCharacters.get(i);
      resultMap.put(key, i);
    }

    return unmodifiableMap(resultMap);
  }

  private static List<Character> getSupportedCharactersAsList() {
    return SUPPORTED_CHARACTERS_STRING.chars().mapToObj(character -> (char) character).distinct().sorted().toList();
  }
}
