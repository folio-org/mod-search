package org.folio.search.service.converter;

import static com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import static org.folio.search.utils.TestUtils.populatedByField;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.resourceDescription;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.collections4.MapUtils;
import org.folio.search.domain.dto.ResourceEventBody;
import org.folio.search.model.SearchDocumentBody;
import org.folio.search.model.metadata.FieldDescription;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.folio.search.service.setter.FieldSetter;
import org.folio.search.utils.JsonConverter;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class SearchDocumentConverterTest {

  private SearchDocumentConverter documentMapper;
  @Mock private ResourceDescriptionService descriptionService;
  @Spy private final ObjectMapper objectMapper = new ObjectMapper()
    .configure(ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
  @Spy private final JsonConverter jsonConverter = new JsonConverter(objectMapper);

  @BeforeEach
  void setUpConverter() {
    Map<String, FieldSetter<?>> setters = Map.of("testSetter",
      map -> MapUtils.getString(map, "baseProperty"));

    documentMapper = new SearchDocumentConverter(jsonConverter, descriptionService, setters);
  }

  @Test
  void convertSingleEvent_positive() {
    var id = randomId();
    var jsonBody = getResourceTestData(id);
    var eventBody = eventBody(RESOURCE_NAME, jsonBody);
    var resourceDescription = resourceDescription(getDescriptionFields(), List.of("$.language"));

    when(descriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription);

    var actual = convert(eventBody);

    assertThat(actual).isEqualTo(expectedSearchDocument(id));
    verify(jsonConverter).toJson(anyMap());
  }

  @Test
  void convertMultipleEvents_positive() {
    var firstEventId = randomId();
    var secondEventId = randomId();
    var firstEvent = eventBody(RESOURCE_NAME, getResourceTestData(firstEventId));
    var secondEvent = eventBody(RESOURCE_NAME, getResourceTestData(secondEventId));

    when(descriptionService.get(RESOURCE_NAME))
      .thenReturn(resourceDescription(getDescriptionFields(), List.of("$.language")));

    var actual = documentMapper.convert(convertConfig("eng"),
      List.of(firstEvent, secondEvent));

    assertThat(actual).containsExactlyInAnyOrder(
      expectedSearchDocument(firstEventId), expectedSearchDocument(secondEventId));
  }

  @Test
  void convertSingleEvent_negative_pathNotFound() {
    var id = randomId();
    var eventBody = eventBody(RESOURCE_NAME, mapOf("id", id));
    var resourceDescription = resourceDescription(mapOf(
      "id", plainField("keyword"),
      "title", plainField("keyword")));

    when(descriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription);

    var actual = convert(eventBody);

    assertThat(actual).isEqualTo(expectedSearchDocument(jsonObject("id", id)));
  }

  @Test
  void convertSingleEvent_negative_emptyTitle() {
    var id = randomId();
    var eventBody = eventBody(RESOURCE_NAME, mapOf("id", id, "title", ""));
    var resourceDescription = resourceDescription(
      mapOf("id", plainField("keyword"), "title", plainField("keyword")));

    when(descriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription);

    var actual = convert(eventBody);

    assertThat(actual)
      .isEqualTo(expectedSearchDocument(jsonObject("id", id, "title", "")));
  }

  @Test
  void convertSingleEvent_positive_multilangResource() {
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

    var actual = convert(eventBody);
    var expectedJson = jsonObject("id", id, "title", jsonObject("eng", "val", "src", "val"));

    assertThat(actual).isEqualTo(expectedSearchDocument(expectedJson));
  }

  @Test
  void convertSingleEvent_positive_repeatableObjectField() {
    var id = randomId();
    var resourceDescription = resourceDescription(mapOf(
      "id", keywordField(), "identifiers", objectField(mapOf("value", keywordField()))));
    var resourceEventBody = eventBody(RESOURCE_NAME, mapOf("id", id, "identifiers", List.of(
      mapOf("type", "isbn", "value", "test-isbn"),
      mapOf("type", "issn", "value", "test-issn"),
      mapOf("type", "isbn"), "test-isbn-2")));

    when(descriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription);

    var actual = convert(resourceEventBody);
    var expectedJson = jsonObject("id", id, "identifiers", jsonArray(
        jsonObject("value", "test-isbn"),
        jsonObject("value", "test-issn")));

    assertThat(actual).isEqualTo(expectedSearchDocument(expectedJson));
  }

  @Test
  void convertSingleEvent_positive_multiLanguageValue() {
    var id = randomId();
    var resourceDescription = resourceDescription(mapOf(
      "id", keywordField(), "alternativeTitle", objectField(mapOf("value", multilangField()))));
    var resourceEventBody = eventBody(RESOURCE_NAME, mapOf("id", id,
      "alternativeTitle", List.of(mapOf("value", "title1"), mapOf("value", null), emptyMap(), "title3")));

    when(descriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription);
    var actual = convert(resourceEventBody);
    var expectedJson = jsonObject(
      "id", id, "alternativeTitle", jsonArray(jsonObject("value", jsonObject("src", "title1"))));
    assertThat(actual).isEqualTo(expectedSearchDocument(expectedJson));
  }

  @Test
  void convertSingleEvent_negative_dataIsNull() {
    var eventBody = eventBody(RESOURCE_NAME, null);
    var actual = documentMapper.convert(convertConfig("eng"), List.of(eventBody));
    assertThat(actual).isEmpty();
  }

  @Test
  void shouldUseOnlyTenantSupportedLanguages() {
    var firstEventId = randomId();
    var secondEventId = randomId();

    var firstTenantName = "first";
    var secondTenantName = "second";
    var firstTenantLanguage = "eng";
    var secondTenantLanguage = "rus";

    var firstEvent = eventBody(RESOURCE_NAME,
      getResourceTestData(firstEventId, firstTenantLanguage)).tenant(firstTenantName);
    var secondEvent = eventBody(RESOURCE_NAME,
      getResourceTestData(secondEventId, secondTenantLanguage)).tenant(secondTenantName);

    when(descriptionService.get(RESOURCE_NAME))
      .thenReturn(resourceDescription(getDescriptionFields(), List.of("$.language")));

    final var convertConfig = new ConvertConfig()
      .addSupportedLanguage(firstTenantName, Set.of(firstTenantLanguage))
      .addSupportedLanguage(secondTenantName, Set.of(secondTenantLanguage));

    var actual = documentMapper.convert(convertConfig, List.of(firstEvent, secondEvent));

    final var firstExpectedJson = asJsonString(getExpectedDocument(firstEventId,
      firstTenantLanguage));
    final var secondExpectedJson = asJsonString(getExpectedDocument(secondEventId,
      secondTenantLanguage));
    assertThat(actual).containsExactlyInAnyOrder(
      SearchDocumentBody.of(firstEventId, firstTenantName, "test-resource_first", firstExpectedJson),
      SearchDocumentBody.of(secondEventId, secondTenantName, "test-resource_second", secondExpectedJson));
  }

  @Test
  void shouldConvertPopulatedByProperty() {
    var id = randomId();
    var resourceDescription = resourceDescription(mapOf(
      "id", keywordField(),
      "baseProperty", keywordField(),
      "populatedProperty", populatedByField("testSetter")
    ));

    var resourceEventBody = eventBody(RESOURCE_NAME, mapOf(
      "id", id,
      "baseProperty", "base property value"
    ));
    when(descriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription);

    var actual = convert(resourceEventBody);

    var expectedJson = asJsonString(jsonObject(
      "id", id,
      "baseProperty", "base property value",
      "populatedProperty", "base property value"
    ));

    assertThat(actual)
      .isEqualTo(SearchDocumentBody.of(id, TENANT_ID, INDEX_NAME, expectedJson));
  }

  @Test
  void shouldThrowIllegalArgumentIfNoSetterForPopulatedProperty() {
    var id = randomId();
    var resourceDescription = resourceDescription(mapOf(
      "id", keywordField(),
      "populatedProperty", populatedByField("undefinedSetter")
    ));

    var resourceEventBody = eventBody(RESOURCE_NAME, mapOf("id", id));
    when(descriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription);

    assertThatThrownBy(() -> convert(resourceEventBody))
      .hasMessage("There is no such property setter: undefinedSetter")
      .isInstanceOf(IllegalArgumentException.class);
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

  private static Map<String, Object> getResourceTestData(String id, String language) {
    return mapOf(
      "id", id,
      "title", List.of("instance title"),
      "language", language,
      "multilang_value", "some value",
      "bool", true,
      "number", 123,
      "numbers", List.of(1, 2, 3, 4),
      "ignored_field", "ignored value",
      "metadata", mapOf(
        "createdAt", "12-01-01T12:03:12Z"));
  }

  private static Map<String, Object> getResourceTestData(String id) {
    return getResourceTestData(id, "eng");
  }

  private static ObjectNode getExpectedDocument(String id, String language) {
    return jsonObject(
      "id", id,
      "title", jsonArray("instance title"),
      "language", language,
      "multilang_value", jsonObject(
        language, "some value",
        "src", "some value"),
      "bool", true,
      "number", 123,
      "numbers", jsonArray(1, 2, 3, 4),
      "metadata", jsonObject(
        "createdAt", "12-01-01T12:03:12Z"));
  }

  private static ObjectNode getExpectedDocument(String id) {
    return getExpectedDocument(id, "eng");
  }

  private SearchDocumentBody expectedSearchDocument(ObjectNode expectedJson) {
    final var id = expectedJson.get("id").asText();
    return SearchDocumentBody.of(id, TENANT_ID, INDEX_NAME, asJsonString(expectedJson));
  }

  private SearchDocumentBody expectedSearchDocument(String id) {
    return expectedSearchDocument(getExpectedDocument(id));
  }

  private ConvertConfig convertConfig(String ... languageCodes) {
    return new ConvertConfig().addSupportedLanguage(TENANT_ID, Set.of(languageCodes));
  }

  private SearchDocumentBody convert(ResourceEventBody eventBody) {
    final var converted = documentMapper.convert(convertConfig("eng"),
      List.of(eventBody));

    assertThat(converted).hasSize(1);

    return converted.get(0);
  }
}
