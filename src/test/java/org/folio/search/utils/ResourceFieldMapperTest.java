package org.folio.search.utils;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.keywordField;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.multilangField;
import static org.folio.search.utils.TestUtils.objectField;
import static org.folio.search.utils.TestUtils.plainField;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.folio.search.model.metadata.FieldDescription;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class ResourceFieldMapperTest {

  private static final List<String> LANGUAGES = List.of("eng");

  @Test
  void convertMapUsingResourceFields_happyPath_plainAndObjectFields() {
    Map<String, FieldDescription> fields = mapOf(
      "id", keywordField(),
      "title", keywordField(),
      "metadata", objectField(mapOf("createdAt", plainField("keyword")))
    );

    Map<String, Object> data = mapOf(
      "id", "id-1",
      "title", "Test Title",
      "metadata", mapOf("createdAt", "2024-01-01")
    );

    var result = ResourceFieldMapper.convertMapUsingResourceFields(data, fields, LANGUAGES, TENANT_ID);

    assertThat(result)
      .containsEntry("id", "id-1")
      .containsEntry("title", "Test Title")
      .containsKey("metadata");
    @SuppressWarnings("unchecked")
    var metadata = (Map<String, Object>) result.get("metadata");
    assertThat(metadata).containsEntry("createdAt", "2024-01-01");
  }

  @Test
  void convertMapUsingResourceFields_happyPath_multilangExpansion() {
    Map<String, FieldDescription> fields = mapOf(
      "id", keywordField(),
      "title", multilangField()
    );

    Map<String, Object> data = mapOf(
      "id", "id-1",
      "title", "Some Title"
    );

    var result = ResourceFieldMapper.convertMapUsingResourceFields(data, fields, LANGUAGES, TENANT_ID);

    assertThat(result).containsEntry("id", "id-1");
    @SuppressWarnings("unchecked")
    var titleMap = (Map<String, Object>) result.get("title");
    assertThat(titleMap)
      .containsEntry("eng", "Some Title")
      .containsEntry("src", "Some Title");
    assertThat(result).containsEntry("plain_title", "Some Title");
  }

  @Test
  void convertMapUsingResourceFields_nestedObjectFields_recursesCorrectly() {
    Map<String, FieldDescription> innerFields = mapOf(
      "value", keywordField()
    );
    Map<String, FieldDescription> fields = mapOf(
      "identifiers", objectField(innerFields)
    );

    Map<String, Object> data = mapOf(
      "identifiers", List.of(
        mapOf("type", "isbn", "value", "isbn-1"),
        mapOf("type", "issn", "value", "issn-1")
      )
    );

    var result = ResourceFieldMapper.convertMapUsingResourceFields(data, fields, LANGUAGES, TENANT_ID);

    assertThat(result).containsKey("identifiers");
    @SuppressWarnings("unchecked")
    var identifiers = (List<Map<String, Object>>) result.get("identifiers");
    assertThat(identifiers).hasSize(2);
    assertThat(identifiers.get(0)).containsEntry("value", "isbn-1");
    assertThat(identifiers.get(1)).containsEntry("value", "issn-1");
  }

  @Test
  void convertMapUsingResourceFields_fieldWithIndexNone_excludedFromOutput() {
    Map<String, FieldDescription> fields = mapOf(
      "id", keywordField(),
      "ignored", plainField("none")
    );

    Map<String, Object> data = mapOf(
      "id", "id-1",
      "ignored", "should not appear"
    );

    var result = ResourceFieldMapper.convertMapUsingResourceFields(data, fields, LANGUAGES, TENANT_ID);

    assertThat(result)
      .containsEntry("id", "id-1")
      .doesNotContainKey("ignored");
  }

  @Test
  void convertMapUsingResourceFields_tenantField_returnsTenantId() {
    var tenantField = keywordField();
    tenantField.setTenantField(true);

    Map<String, FieldDescription> fields = new LinkedHashMap<>();
    fields.put("id", keywordField());
    fields.put("tenantId", tenantField);

    Map<String, Object> data = mapOf(
      "id", "id-1",
      "tenantId", "original-value-ignored"
    );

    var result = ResourceFieldMapper.convertMapUsingResourceFields(data, fields, LANGUAGES, TENANT_ID);

    assertThat(result)
      .containsEntry("id", "id-1")
      .containsEntry("tenantId", TENANT_ID);
  }

  @Test
  void convertMapUsingResourceFields_emptyRawRecord_returnsNull() {
    Map<String, FieldDescription> fields = mapOf(
      "id", keywordField(),
      "title", keywordField()
    );

    var result = ResourceFieldMapper.convertMapUsingResourceFields(emptyMap(), fields, LANGUAGES, TENANT_ID);

    assertThat(result).isNull();
  }
}
