package org.folio.search.service.converter;

import static com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.JsonUtils.jsonArray;
import static org.folio.search.utils.JsonUtils.jsonObject;
import static org.folio.search.utils.TestConstants.INDEX_NAME;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.asJsonString;
import static org.folio.search.utils.TestUtils.eventBody;
import static org.folio.search.utils.TestUtils.keywordField;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.multilangField;
import static org.folio.search.utils.TestUtils.objectField;
import static org.folio.search.utils.TestUtils.plainField;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.resourceDescription;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.folio.search.model.SearchDocumentBody;
import org.folio.search.model.metadata.FieldDescription;
import org.folio.search.service.LanguageConfigService;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.folio.search.utils.JsonConverter;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class SearchDocumentConverterTest {

  @InjectMocks
  private SearchDocumentConverter documentMapper;
  @Mock
  private ResourceDescriptionService descriptionService;
  @Mock
  private LanguageConfigService languageConfigService;
  @Spy
  private final ObjectMapper objectMapper = new ObjectMapper().configure(ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
  @Spy
  private final JsonConverter jsonConverter = new JsonConverter(objectMapper);

  @Test
  void convertSingle_positive() {
    var id = randomId();
    var jsonBody = getResourceTestData(id);
    var eventBody = eventBody(RESOURCE_NAME, jsonBody);
    var resourceDescription = resourceDescription(getDescriptionFields(), List.of("$.language"));

    when(descriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription);
    when(languageConfigService.getAllLanguagesForTenant(any())).thenReturn(Set.of("eng"));

    var actual = documentMapper.convert(eventBody);
    var expectedJson = asJsonString(getExpectedDocument(id));

    assertThat(actual).isPresent().get().isEqualTo(SearchDocumentBody.of(id, TENANT_ID, INDEX_NAME, expectedJson));
    verify(jsonConverter).toJson(anyMap());
  }

  @Test
  void convertMultiple_positive() {
    var id = randomId();
    var resourceData = getResourceTestData(id);
    var eventBody = eventBody(RESOURCE_NAME, resourceData);
    var resourceDescription = resourceDescription(getDescriptionFields(), List.of("$.language"));

    when(descriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription);
    when(languageConfigService.getAllLanguagesForTenant(any())).thenReturn(Set.of("eng"));

    var actual = documentMapper.convert(List.of(eventBody));

    var expectedJson = asJsonString(getExpectedDocument(id));
    assertThat(actual).isEqualTo(List.of(SearchDocumentBody.of(id, TENANT_ID, INDEX_NAME, expectedJson)));
  }

  @Test
  void convertSingle_negative_pathNotFound() {
    var id = randomId();
    var eventBody = eventBody(RESOURCE_NAME, mapOf("id", id));
    var resourceDescription = resourceDescription(mapOf(
      "id", plainField("keyword"),
      "title", plainField("keyword")));

    when(descriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription);
    when(languageConfigService.getAllLanguagesForTenant(any())).thenReturn(Set.of("eng"));

    var actual = documentMapper.convert(eventBody);
    var expectedJson = asJsonString(jsonObject("id", id));

    assertThat(actual).isPresent().get().isEqualTo(SearchDocumentBody.of(id, TENANT_ID, INDEX_NAME, expectedJson));
  }

  @Test
  void convertSingle_negative_emptyTitle() {
    var id = randomId();
    var eventBody = eventBody(RESOURCE_NAME, mapOf("id", id, "title", ""));
    var resourceDescription = resourceDescription(
      mapOf("id", plainField("keyword"), "title", plainField("keyword")));

    when(descriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription);
    when(languageConfigService.getAllLanguagesForTenant(any())).thenReturn(Set.of("eng"));

    var actual = documentMapper.convert(eventBody);
    var expectedJson = asJsonString(jsonObject("id", id, "title", ""));

    assertThat(actual).isPresent().get().isEqualTo(SearchDocumentBody.of(id, TENANT_ID, INDEX_NAME, expectedJson));
  }

  @Test
  void convertSingle_positive_multilangResource() {
    var id = randomId();
    var eventBody = eventBody(RESOURCE_NAME, mapOf(
      "id", id, "title", "val",
      "lang1", List.of("eng"),
      "lang2", mapOf("value", "eng"),
      "lang3", List.of(1, 2),
      "lang4", "eng",
      "lang5", true));

    var resourceDescription = resourceDescription(
      mapOf("id", plainField("keyword"), "title", multilangField()),
      List.of("$.lang1", "$.lang2", "$.lang3", "$.lang4", "$.lang5"));

    when(descriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription);
    when(languageConfigService.getAllLanguagesForTenant(any())).thenReturn(Set.of("eng"));

    var actual = documentMapper.convert(eventBody);
    var expectedJson = asJsonString(jsonObject("id", id, "title", jsonObject("eng", "val", "src", "val")));

    assertThat(actual).isPresent().get().isEqualTo(SearchDocumentBody.of(id, TENANT_ID, INDEX_NAME, expectedJson));
  }

  @Test
  void convertSingle_positive_repeatableObjectField() {
    var id = randomId();
    var resourceDescription = resourceDescription(mapOf(
      "id", keywordField(), "identifiers", objectField(mapOf("value", keywordField()))));
    var resourceEventBody = eventBody(RESOURCE_NAME, mapOf("id", id, "identifiers", List.of(
      mapOf("type", "isbn", "value", "test-isbn"),
      mapOf("type", "issn", "value", "test-issn"),
      mapOf("type", "isbn"), "test-isbn-2")));

    when(descriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription);

    var actual = documentMapper.convert(resourceEventBody);
    var expectedJson = asJsonString(jsonObject(
      "id", id, "identifiers", jsonArray(jsonObject("value", "test-isbn"), jsonObject("value", "test-issn"))));

    assertThat(actual).isPresent().get().isEqualTo(SearchDocumentBody.of(id, TENANT_ID, INDEX_NAME, expectedJson));
  }

  @Test
  void convertSingle_positive_multiLanguageValue() {
    var id = randomId();
    var resourceDescription = resourceDescription(mapOf(
      "id", keywordField(), "alternativeTitle", objectField(mapOf("value", multilangField()))));
    var resourceEventBody = eventBody(RESOURCE_NAME, mapOf("id", id,
      "alternativeTitle", List.of(mapOf("value", "title1"), mapOf("value", null), emptyMap(), "title3")));

    when(descriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription);
    var actual = documentMapper.convert(resourceEventBody);
    var expectedJson = asJsonString(jsonObject(
      "id", id, "alternativeTitle", jsonArray(jsonObject("value", jsonObject("src", "title1")))));
    assertThat(actual).isPresent().get().isEqualTo(SearchDocumentBody.of(id, TENANT_ID, INDEX_NAME, expectedJson));
  }

  @Test
  void convertSingle_negative_dataIsNull() {
    var eventBody = eventBody(RESOURCE_NAME, null);
    var actual = documentMapper.convert(eventBody);
    assertThat(actual).isNotPresent();
  }

  private static Map<String, FieldDescription> getDescriptionFields() {
    return mapOf(
      "id", keywordField(),
      "title", keywordField(),
      "language", keywordField(),
      "multilang_value", multilangField(),
      "bool", plainField("boolean"),
      "number", plainField("numeric"),
      "numbers", plainField("numeric"),
      "ignored_field", plainField("none"),
      "metadata", objectField(mapOf(
        "createdAt", plainField("keyword"))));
  }

  private static Map<String, Object> getResourceTestData(String id) {
    return mapOf(
      "id", id,
      "title", List.of("instance title"),
      "language", "eng",
      "multilang_value", "some value",
      "bool", true,
      "number", 123,
      "numbers", List.of(1, 2, 3, 4),
      "ignored_field", "ignored value",
      "metadata", mapOf(
        "createdAt", "12-01-01T12:03:12Z"));
  }

  private static ObjectNode getExpectedDocument(String id) {
    return jsonObject(
      "id", id,
      "title", jsonArray("instance title"),
      "language", "eng",
      "multilang_value", jsonObject(
        "eng", "some value",
        "src", "some value"),
      "bool", true,
      "number", 123,
      "numbers", jsonArray(1, 2, 3, 4),
      "metadata", jsonObject(
        "createdAt", "12-01-01T12:03:12Z"));
  }
}
