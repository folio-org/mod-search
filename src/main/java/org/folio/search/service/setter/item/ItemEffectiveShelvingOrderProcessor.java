package org.folio.search.service.setter.item;

import static java.util.Collections.unmodifiableMap;
import static java.util.Locale.ROOT;
import static org.folio.search.utils.CollectionUtils.toLinkedHashSet;
import static org.folio.search.utils.CollectionUtils.toStreamSafe;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
public class ItemEffectiveShelvingOrderProcessor implements FieldProcessor<Instance, Set<String>> {

  private static final char ASCII_SPACE = ' ';
  private static final int MAX_SUPPORTED_CHARACTERS = 52;
  private static final String SUPPORTED_CHARACTERS_STRING = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ .,:;=-+~_/\\#$@?";
  private static final Map<Character, Integer> VALID_CHARACTERS_MAP = getValidCharactersMap();

  @Override
  public Set<String> getFieldValue(Instance eventBody) {
    return toStreamSafe(eventBody.getItems())
      .map(Item::getEffectiveShelvingOrder)
      .map(ItemEffectiveShelvingOrderProcessor::normalizeValue)
      .filter(StringUtils::isNotBlank)
      .sorted()
      .collect(toLinkedHashSet());
  }

  /**
   * Normalizes incoming call-number by removing unsupported characters.
   *
   * @param effectiveShelvingOrder - effective shelving order as {@link String} object
   * @return normalized effective shelving order value
   */
  public static String normalizeValue(String effectiveShelvingOrder) {
    if (effectiveShelvingOrder == null) {
      return null;
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
    return SUPPORTED_CHARACTERS_STRING.chars()
      .mapToObj(character -> (char) character)
      .distinct()
      .sorted()
      .toList();
  }
}
