package org.folio.search.service.converter;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.model.types.IndexActionType.DELETE;
import static org.folio.search.model.types.IndexActionType.INDEX;
import static org.folio.search.utils.JsonUtils.jsonArray;
import static org.folio.search.utils.JsonUtils.jsonObject;
import static org.folio.search.utils.TestConstants.RESOURCE_ID;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;
import static org.folio.search.utils.TestUtils.asJsonString;
import static org.folio.search.utils.TestUtils.keywordField;
import static org.folio.search.utils.TestUtils.keywordFieldWithDefaultValue;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.multilangField;
import static org.folio.search.utils.TestUtils.objectField;
import static org.folio.search.utils.TestUtils.plainField;
import static org.folio.search.utils.TestUtils.resourceDescription;
import static org.folio.search.utils.TestUtils.resourceEvent;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.model.converter.ConversionContext;
import org.folio.search.model.index.SearchDocumentBody;
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

  @InjectMocks private SearchDocumentConverter documentMapper;
  @Mock private LanguageConfigService languageConfigService;
  @Mock private SearchFieldsProcessor searchFieldsProcessor;
  @Mock private ResourceDescriptionService descriptionService;
  @Spy private final JsonConverter jsonConverter = new JsonConverter(OBJECT_MAPPER);

  @Test
  void convert_positive() {
    when(languageConfigService.getAllLanguageCodes()).thenReturn(Set.of("eng"));
    when(descriptionService.get(RESOURCE_NAME)).thenReturn(
      resourceDescription(resourceDescriptionFields(), List.of("$.language")));
    var resourceEvent = resourceEvent(RESOURCE_NAME, testResourceBody());

    var actual = documentMapper.convert(resourceEvent);

    assertThat(actual).isEqualTo(expectedSearchDocument(resourceEvent, expectedSearchDocumentBody()));
    verify(jsonConverter).toJson(anyMap());
  }

  @Test
  void convert_deleteEvent() {
    var event = resourceEvent(RESOURCE_ID, RESOURCE_NAME, ResourceEventType.DELETE, null, emptyMap());
    var actual = documentMapper.convert(event);
    assertThat(actual).isPresent().get().isEqualTo(SearchDocumentBody.of(null, event, DELETE));
  }

  @Test
  void convert_negative_pathNotFound() {
    when(descriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription(
      mapOf("id", plainField("keyword"), "title", plainField("keyword"))));
    var resourceEvent = resourceEvent(RESOURCE_NAME, mapOf("id", RESOURCE_ID));
    var actual = documentMapper.convert(resourceEvent);
    assertThat(actual).isEqualTo(expectedSearchDocument(resourceEvent, jsonObject("id", RESOURCE_ID)));
  }

  @Test
  void convert_negative_emptyTitle() {
    when(descriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription(
      mapOf("id", plainField("keyword"), "title", plainField("keyword"))));
    var resourceEvent = resourceEvent(RESOURCE_NAME, mapOf("id", RESOURCE_ID, "title", ""));
    var actual = documentMapper.convert(resourceEvent);
    assertThat(actual).isEqualTo(expectedSearchDocument(resourceEvent, jsonObject("id", RESOURCE_ID, "title", "")));
  }

  @Test
  void convert_positive_multilangResource() {
    when(languageConfigService.getAllLanguageCodes()).thenReturn(Set.of("eng"));
    when(descriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription(
      mapOf("id", plainField("keyword"), "title", multilangField()),
      List.of("$.lang1", "$.lang2", "$.lang3", "$.lang4", "$.lang5")));

    var resourceEvent = resourceEvent(RESOURCE_NAME, mapOf(
      "id", RESOURCE_ID,
      "title", "val",
      "lang1", List.of("eng"),
      "lang2", mapOf("value", "eng"),
      "lang3", List.of(1, 2),
      "lang4", "eng",
      "lang5", true));

    var actual = documentMapper.convert(resourceEvent);

    assertThat(actual).isEqualTo(expectedSearchDocument(resourceEvent,
      jsonObject("id", RESOURCE_ID, "title", jsonObject("eng", "val", "src", "val"), "plain_title", "val")));
  }

  @Test
  void convert_positive_repeatableObjectField() {
    when(descriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription(mapOf(
      "id", keywordField(), "identifiers", objectField(mapOf("value", keywordField())))));
    var resourceEvent = resourceEvent(RESOURCE_NAME, mapOf("id", RESOURCE_ID, "identifiers", List.of(
      mapOf("type", "isbn", "value", "test-isbn"),
      mapOf("type", "issn", "value", "test-issn"),
      mapOf("type", "isbn"), "test-isbn-2")));

    var actual = documentMapper.convert(resourceEvent);

    assertThat(actual).isEqualTo(expectedSearchDocument(resourceEvent, jsonObject("id", RESOURCE_ID,
      "identifiers", jsonArray(jsonObject("value", "test-isbn"), jsonObject("value", "test-issn")))));
  }

  @Test
  void convert_positive_multiLanguageValue() {
    var resourceDescription = resourceDescription(mapOf(
      "id", keywordField(), "alternativeTitle", objectField(mapOf("value", multilangField()))));
    var resourceEvent = resourceEvent(RESOURCE_NAME, mapOf("id", RESOURCE_ID,
      "alternativeTitle", List.of(mapOf("value", "title1"), mapOf("value", null), emptyMap(), "title3")));

    when(descriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription);

    var actual = documentMapper.convert(resourceEvent);

    assertThat(actual).isEqualTo(expectedSearchDocument(resourceEvent, jsonObject("id", RESOURCE_ID,
      "alternativeTitle", jsonArray(jsonObject("value", jsonObject("src", "title1"), "plain_value", "title1")))));
  }

  @Test
  void convert_negative_dataIsNull() {
    var resourceEvent = resourceEvent(RESOURCE_NAME, null);
    var actual = documentMapper.convert(resourceEvent);
    assertThat(actual).isEmpty();
  }

  @Test
  void convert_positive_extendedFields() {
    var desc = resourceDescription(mapOf("id", keywordField(), "base", keywordField()));
    var resourceEvent = resourceEvent(RESOURCE_NAME, mapOf("id", RESOURCE_ID, "base", "base val"));
    var expectedContext = ConversionContext.of(resourceEvent, desc, emptyList());

    when(descriptionService.get(RESOURCE_NAME)).thenReturn(desc);
    when(searchFieldsProcessor.getSearchFields(expectedContext)).thenReturn(mapOf("generated", "generated value"));

    var actual = documentMapper.convert(resourceEvent);

    assertThat(actual).isEqualTo(expectedSearchDocument(resourceEvent, jsonObject(
      "id", RESOURCE_ID, "base", "base val", "generated", "generated value")));
  }

  @Test
  void convert_positive_useDefaultValueFromFieldDescription() {
    when(descriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription(mapOf(
      "id", keywordField(), "value", keywordFieldWithDefaultValue("default"))));
    var event = resourceEvent(RESOURCE_NAME, Map.of("id", RESOURCE_ID, "value", "aValue"));
    var actual = documentMapper.convert(event);
    assertThat(actual).isEqualTo(expectedSearchDocument(event, jsonObject("id", RESOURCE_ID, "value", "aValue")));
  }

  @Test
  void convert_positive_useDefaultValueWhenNoValuePresent() {
    var resourceDescription = resourceDescription(mapOf("id", keywordField(),
      "value", keywordFieldWithDefaultValue("default")));

    when(descriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription);

    var event = resourceEvent(RESOURCE_NAME, Map.of("id", RESOURCE_ID));
    var actual = documentMapper.convert(event);

    assertThat(actual).isEqualTo(expectedSearchDocument(event, jsonObject("id", RESOURCE_ID, "value", "default")));
  }

  @Test
  void convert_positive_useDefaultWhenValueIsNull() {
    when(descriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription(mapOf(
      "id", keywordField(), "value", keywordFieldWithDefaultValue("default"))));
    var event = resourceEvent(RESOURCE_NAME, mapOf("id", RESOURCE_ID, "value", null));
    var actual = documentMapper.convert(event);
    assertThat(actual).isEqualTo(expectedSearchDocument(event, jsonObject("id", RESOURCE_ID, "value", "default")));
  }

  @Test
  void convert_positive_languageIsNotSpecified() {
    var event = resourceEvent(RESOURCE_NAME, mapOf("id", RESOURCE_ID, "language", "rus", "multilang_value", "value"));

    when(languageConfigService.getAllLanguageCodes()).thenReturn(Set.of("eng", "fra"));
    when(descriptionService.get(RESOURCE_NAME)).thenReturn(
      resourceDescription(resourceDescriptionFields(), List.of("$.language")));

    var actual = documentMapper.convert(event);

    assertThat(actual).isEqualTo(expectedSearchDocument(event, jsonObject("id", RESOURCE_ID, "language", "rus",
      "multilang_value", jsonObject("src", "value"), "plain_multilang_value", "value")));
  }

  @Test
  void convert_positive_instanceWithItems() {
    var event = resourceEvent(RESOURCE_NAME, mapOf("id", RESOURCE_ID, "items", List.of(
      mapOf("id", "item#1"),
      mapOf("id", "item#2", "effectiveShelvingOrder", "F10"),
      mapOf("id", "item#3", "effectiveShelvingOrder", "C5"),
      mapOf("id", "item#4"))));

    when(languageConfigService.getAllLanguageCodes()).thenReturn(emptySet());
    when(descriptionService.get(RESOURCE_NAME)).thenReturn(
      resourceDescription(mapOf("id", keywordField(), "items", objectField(
        mapOf("id", keywordField(), "effectiveShelvingOrder", keywordField())))));

    var actual = documentMapper.convert(event);

    assertThat(actual).isEqualTo(expectedSearchDocument(event, jsonObject(
      "id", RESOURCE_ID,
      "items", jsonArray(
        jsonObject("id", "item#3", "effectiveShelvingOrder", "C5"),
        jsonObject("id", "item#2", "effectiveShelvingOrder", "F10"),
        jsonObject("id", "item#1"), jsonObject("id", "item#4")))));
  }

  private static Map<String, FieldDescription> resourceDescriptionFields() {
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

  private static Map<String, Object> testResourceBody() {
    return mapOf(
      "id", RESOURCE_ID,
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

  private static ObjectNode expectedSearchDocumentBody() {
    return jsonObject(
      "id", RESOURCE_ID,
      "title", jsonArray("instance title"),
      "language", "eng",
      "multilang_value", jsonObject("eng", "some value", "src", "some value"),
      "plain_multilang_value", "some value",
      "bool", true,
      "number", 123,
      "numbers", jsonArray(1, 2, 3, 4),
      "metadata", jsonObject(
        "createdAt", "12-01-01T12:03:12Z"));
  }

  private static Optional<SearchDocumentBody> expectedSearchDocument(ResourceEvent event, ObjectNode expectedJson) {
    return Optional.of(SearchDocumentBody.of(asJsonString(expectedJson), event, INDEX));
  }
}
