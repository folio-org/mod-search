package org.folio.search.utils;

import static java.util.Collections.singletonList;
import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;
import static org.folio.search.utils.TestUtils.toMap;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.folio.search.domain.dto.Authority;
import org.folio.search.model.metadata.AuthorityFieldDescription;
import org.folio.search.model.metadata.PlainFieldDescription;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AuthoritySearchUtils {

  private static final List<String> AUTHORITY_COMMON_FIELDS = List.of(
    "id", "identifiers", "subjectHeadings", "metadata", "notes");

  @SneakyThrows
  public static Authority expectedAuthority(Authority authority, Map<String, Object> searchFields, String... fields) {
    var sourceMap = expectedAuthorityAsMap(authority, fields);
    sourceMap.putAll(searchFields);
    return OBJECT_MAPPER.convertValue(sourceMap, Authority.class);
  }

  @SneakyThrows
  public static Authority expectedAuthority(Authority authority, String... fields) {
    return OBJECT_MAPPER.convertValue(expectedAuthorityAsMap(authority, fields), Authority.class);
  }

  public static Map<String, Object> expectedAuthorityAsMap(Authority source, String... fields) {
    var resultMap = new LinkedHashMap<String, Object>();
    var sourceMap = toMap(source);
    copyExpectedEntityFields(sourceMap, resultMap, List.of(fields));
    copyExpectedEntityFields(sourceMap, resultMap, AUTHORITY_COMMON_FIELDS);
    return resultMap;
  }

  /**
   * Test method that allows to copy value from source to target using top-level keys. if value from list of fields to
   * copy contains value in format {@code fieldName[arrayIndex], example: name[0]} it will be extracted to separate list
   * containing this value only.
   */
  public static void copyExpectedEntityFields(Map<String, Object> src, Map<String, Object> t, List<String> fields) {
    var pattern = Pattern.compile("(.+)\\[(\\d+)]");
    for (var field : fields) {
      var matcher = pattern.matcher(field);
      if (matcher.matches()) {
        var fieldName = matcher.group(1);
        var arrayIndex = Integer.parseInt(matcher.group(2));
        var value = src.get(fieldName);
        if (src.containsKey(fieldName)) {
          t.put(fieldName, (value instanceof List<?>) ? singletonList(((List<?>) value).get(arrayIndex)) : value);
        }
        continue;
      }
      if (src.containsKey(field)) {
        t.put(field, src.get(field));
      }
    }
  }

  public static AuthorityFieldDescription authorityField(String headingType, String authRefType) {
    var authorityFieldDescription = new AuthorityFieldDescription();
    authorityFieldDescription.setIndex(PlainFieldDescription.STANDARD_FIELD_TYPE);
    authorityFieldDescription.setHeadingType(headingType);
    authorityFieldDescription.setAuthRefType(authRefType);
    return authorityFieldDescription;
  }
}
