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
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;
import static org.folio.search.utils.TestUtils.SMILE_MAPPER;
import static org.folio.search.utils.TestUtils.asJsonString;
import static org.folio.search.utils.TestUtils.keywordField;
import static org.folio.search.utils.TestUtils.keywordFieldWithDefaultValue;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.multilangField;
import static org.folio.search.utils.TestUtils.objectField;
import static org.folio.search.utils.TestUtils.plainField;
import static org.folio.search.utils.TestUtils.resourceDescription;
import static org.folio.search.utils.TestUtils.resourceEvent;
import static org.folio.search.utils.TestUtils.spyLambda;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import lombok.SneakyThrows;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.configuration.properties.SearchConfigurationProperties.IndexingSettings;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.model.converter.ConversionContext;
import org.folio.search.model.index.SearchDocumentBody;
import org.folio.search.model.metadata.FieldDescription;
import org.folio.search.model.types.IndexingDataFormat;
import org.folio.search.service.consortium.LanguageConfigServiceDecorator;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.folio.search.utils.JsonConverter;
import org.folio.search.utils.SmileConverter;
import org.folio.spring.test.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.common.bytes.BytesReference;

@UnitTest
@ExtendWith(MockitoExtension.class)
class SearchDocumentConverterTest {

  @Spy
  private final JsonConverter jsonConverter = new JsonConverter(OBJECT_MAPPER);
  @Spy
  private final SmileConverter smileConverter = new SmileConverter();
  private final Function<Map<String, Object>, BytesReference> resultDocumentConverter =
    spyLambda(Function.class, smileConverter::toSmile);
  @InjectMocks
  private SearchDocumentConverter documentMapper;
  @Mock
  private LanguageConfigServiceDecorator languageConfigService;
  @Mock
  private SearchFieldsProcessor searchFieldsProcessor;
  @Mock
  private ResourceDescriptionService descriptionService;
  @Spy
  private SearchConfigurationProperties searchConfigurationProperties = getSearchConfigurationProperties();

  @Test
  void convert_positive() {
    when(languageConfigService.getAllLanguageCodes()).thenReturn(Set.of("eng"));
    when(descriptionService.get(RESOURCE_NAME)).thenReturn(
      resourceDescription(resourceDescriptionFields(), List.of("$.language")));
    var resourceEvent = resourceEvent(RESOURCE_NAME, testResourceBody());

    var actual = documentMapper.convert(resourceEvent);

    assertThat(actual).isEqualTo(expectedSearchDocument(resourceEvent, expectedSearchDocumentBody()));
  }

  @Test
  @SneakyThrows
  void convert_positive_json() {
    var searchConfig = getSearchConfigurationProperties();
    searchConfig.getIndexing().setDataFormat(IndexingDataFormat.JSON);
    searchConfigurationProperties = spy(searchConfig);
    documentMapper = new SearchDocumentConverter(searchFieldsProcessor,
      languageConfigService, descriptionService, searchConfig, jsonConverter::toJsonBytes);

    when(languageConfigService.getAllLanguageCodes()).thenReturn(Set.of("eng"));
    when(descriptionService.get(RESOURCE_NAME)).thenReturn(
      resourceDescription(resourceDescriptionFields(), List.of("$.language")));
    var resourceEvent = resourceEvent(RESOURCE_NAME, testResourceBody());

    var expected = Optional.of(SearchDocumentBody.of(new BytesArray(asJsonString(expectedSearchDocumentBody())),
      IndexingDataFormat.JSON, resourceEvent, INDEX));
    var actual = documentMapper.convert(resourceEvent);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void convert_deleteEvent() {
    var event = resourceEvent(RESOURCE_ID, RESOURCE_NAME, ResourceEventType.DELETE, null, emptyMap());
    var actual = documentMapper.convert(event);
    assertThat(actual).isPresent()
      .get()
      .isEqualTo(SearchDocumentBody.of(null, IndexingDataFormat.SMILE, event, DELETE));
  }

  @Test
  void convert_negative_pathNotFound() {
    when(descriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription(
      mapOf("id", plainField("keyword"), "tenantId", keywordField(), "title", plainField("keyword"))));
    var resourceEvent = resourceEvent(RESOURCE_NAME, mapOf("id", RESOURCE_ID, "tenantId", TENANT_ID));
    var actual = documentMapper.convert(resourceEvent);
    assertThat(actual).isEqualTo(
      expectedSearchDocument(resourceEvent, jsonObject("id", RESOURCE_ID, "tenantId", TENANT_ID)));
  }

  @Test
  void convert_negative_emptyTitle() {
    when(descriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription(
      mapOf("id", plainField("keyword"), "tenantId", keywordField(),
        "title", plainField("keyword"))));
    var resourceEvent = resourceEvent(RESOURCE_NAME, mapOf("id", RESOURCE_ID, "tenantId", TENANT_ID,
      "title", ""));
    var actual = documentMapper.convert(resourceEvent);
    ObjectNode expectedJson = jsonObject("id", RESOURCE_ID, "tenantId", TENANT_ID, "title", "");
    assertThat(actual).isEqualTo(expectedSearchDocument(resourceEvent, expectedJson));
  }

  @Test
  void convert_positive_multilangResource() {
    when(languageConfigService.getAllLanguageCodes()).thenReturn(Set.of("eng"));
    when(descriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription(
      mapOf("id", plainField("keyword"), "tenantId", keywordField(), "title", multilangField()),
      List.of("$.lang1", "$.lang2", "$.lang3", "$.lang4", "$.lang5")));

    var resourceEvent = resourceEvent(RESOURCE_NAME, mapOf(
      "id", RESOURCE_ID,
      "tenantId", TENANT_ID,
      "title", "val",
      "lang1", List.of("eng"),
      "lang2", mapOf("value", "eng"),
      "lang3", List.of(1, 2),
      "lang4", "eng",
      "lang5", true));

    var actual = documentMapper.convert(resourceEvent);

    ObjectNode expectedJson =
      jsonObject("id", RESOURCE_ID, "tenantId", TENANT_ID,
        "title", jsonObject("eng", "val", "src", "val"), "plain_title", "val");
    assertThat(actual).isEqualTo(expectedSearchDocument(resourceEvent,
      expectedJson));
  }

  @Test
  void convert_positive_repeatableObjectField() {
    when(descriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription(mapOf(
      "id", keywordField(), "tenantId", keywordField(),
      "identifiers", objectField(mapOf("value", keywordField())))));
    var resourceEvent = resourceEvent(RESOURCE_NAME, mapOf("id", RESOURCE_ID,
      "tenantId", TENANT_ID,
      "identifiers", List.of(
        mapOf("type", "isbn", "value", "test-isbn"),
        mapOf("type", "issn", "value", "test-issn"),
        mapOf("type", "isbn"), "test-isbn-2")));

    var actual = documentMapper.convert(resourceEvent);

    ObjectNode expectedJson = jsonObject("id", RESOURCE_ID, "tenantId", TENANT_ID,
      "identifiers", jsonArray(jsonObject("value", "test-isbn"), jsonObject("value", "test-issn")));
    assertThat(actual).isEqualTo(expectedSearchDocument(resourceEvent, expectedJson));
  }

  @Test
  void convert_positive_multiLanguageValue() {
    var resourceDescription = resourceDescription(mapOf(
      "id", keywordField(), "tenantId", keywordField(),
      "alternativeTitle", objectField(mapOf("value", multilangField()))));
    var resourceEvent = resourceEvent(RESOURCE_NAME, mapOf("id", RESOURCE_ID, "tenantId", TENANT_ID,
      "alternativeTitle", List.of(mapOf("value", "title1"), mapOf("value", null), emptyMap(), "title3")));

    when(descriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription);

    var actual = documentMapper.convert(resourceEvent);

    ObjectNode expectedJson = jsonObject("id", RESOURCE_ID, "tenantId", TENANT_ID,
      "alternativeTitle", jsonArray(jsonObject("value", jsonObject("src", "title1"), "plain_value", "title1")));
    assertThat(actual).isEqualTo(expectedSearchDocument(resourceEvent, expectedJson));
  }

  @Test
  void convert_negative_dataIsNull() {
    var resourceEvent = resourceEvent(RESOURCE_NAME, null);
    var actual = documentMapper.convert(resourceEvent);
    assertThat(actual).isEmpty();
  }

  @Test
  void convert_positive_extendedFields() {
    var desc = resourceDescription(mapOf("id", keywordField(), "tenantId", keywordField(),
      "base", keywordField()));
    var resourceEvent = resourceEvent(RESOURCE_NAME, mapOf("id", RESOURCE_ID, "tenantId", TENANT_ID,
      "base", "base val"));
    var expectedContext = ConversionContext.of(resourceEvent, desc, emptyList());

    when(descriptionService.get(RESOURCE_NAME)).thenReturn(desc);
    when(searchFieldsProcessor.getSearchFields(expectedContext)).thenReturn(mapOf("generated", "generated value"));

    var actual = documentMapper.convert(resourceEvent);

    ObjectNode expectedJson = jsonObject(
      "id", RESOURCE_ID, "tenantId", TENANT_ID, "base", "base val", "generated", "generated value");
    assertThat(actual).isEqualTo(expectedSearchDocument(resourceEvent, expectedJson));
  }

  @Test
  void convert_positive_useDefaultValueFromFieldDescription() {
    when(descriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription(mapOf(
      "id", keywordField(), "tenantId", keywordField(),
      "value", keywordFieldWithDefaultValue("default"))));
    var event = resourceEvent(RESOURCE_NAME, Map.of("id", RESOURCE_ID, "tenantId", TENANT_ID, "value", "aValue"));
    var actual = documentMapper.convert(event);
    ObjectNode expectedJson = jsonObject("id", RESOURCE_ID, "tenantId", TENANT_ID, "value", "aValue");
    assertThat(actual).isEqualTo(expectedSearchDocument(event, expectedJson));
  }

  @Test
  void convert_positive_useDefaultValueWhenNoValuePresent() {
    var resourceDescription = resourceDescription(mapOf("id", keywordField(),
      "tenantId", keywordField(),
      "value", keywordFieldWithDefaultValue("default")));

    when(descriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription);

    var event = resourceEvent(RESOURCE_NAME, Map.of("id", RESOURCE_ID, "tenantId", TENANT_ID));
    var actual = documentMapper.convert(event);

    ObjectNode expectedJson = jsonObject("id", RESOURCE_ID, "tenantId", TENANT_ID, "value", "default");
    assertThat(actual).isEqualTo(expectedSearchDocument(event, expectedJson));
  }

  @Test
  void convert_positive_useDefaultWhenValueIsNull() {
    when(descriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription(mapOf(
      "id", keywordField(), "tenantId", keywordField(),
      "value", keywordFieldWithDefaultValue("default"))));
    var event = resourceEvent(RESOURCE_NAME, mapOf("id", RESOURCE_ID, "tenantId", TENANT_ID, "value", null));
    var actual = documentMapper.convert(event);
    ObjectNode expectedJson = jsonObject("id", RESOURCE_ID, "tenantId", TENANT_ID, "value", "default");
    assertThat(actual).isEqualTo(expectedSearchDocument(event, expectedJson));
  }

  @Test
  void convert_positive_languageIsNotSpecified() {
    var event = resourceEvent(RESOURCE_NAME, mapOf("id", RESOURCE_ID, "tenantId", TENANT_ID,
      "language", "rus", "multilang_value", "value"));

    when(languageConfigService.getAllLanguageCodes()).thenReturn(Set.of("eng", "fra"));
    when(descriptionService.get(RESOURCE_NAME)).thenReturn(
      resourceDescription(resourceDescriptionFields(), List.of("$.language")));

    var actual = documentMapper.convert(event);

    ObjectNode expectedJson = jsonObject("id", RESOURCE_ID, "tenantId", TENANT_ID, "language", "rus",
      "multilang_value", jsonObject("src", "value"), "plain_multilang_value", "value");
    assertThat(actual).isEqualTo(expectedSearchDocument(event, expectedJson));
  }

  @Test
  void convert_positive_instanceWithItems() {
    var event = resourceEvent(RESOURCE_NAME, mapOf("id", RESOURCE_ID, "tenantId", TENANT_ID, "items", List.of(
      mapOf("id", "item#1"),
      mapOf("id", "item#2", "effectiveShelvingOrder", "F10"),
      mapOf("id", "item#3", "effectiveShelvingOrder", "C5"),
      mapOf("id", "item#4"))));

    when(languageConfigService.getAllLanguageCodes()).thenReturn(emptySet());
    when(descriptionService.get(RESOURCE_NAME)).thenReturn(
      resourceDescription(mapOf("id", keywordField(), "tenantId", keywordField(),
        "items", objectField(mapOf("id", keywordField(), "effectiveShelvingOrder", keywordField())))));

    var actual = documentMapper.convert(event);

    ObjectNode expectedJson = jsonObject(
      "id", RESOURCE_ID,
      "tenantId", TENANT_ID,
      "items", jsonArray(
        jsonObject("id", "item#1"),
        jsonObject("id", "item#2", "effectiveShelvingOrder", "F10"),
        jsonObject("id", "item#3", "effectiveShelvingOrder", "C5"),
        jsonObject("id", "item#4")));
    assertThat(actual).isEqualTo(expectedSearchDocument(event, expectedJson));
  }

  private static Map<String, FieldDescription> resourceDescriptionFields() {
    return mapOf(
      "id", keywordField(),
      "tenantId", keywordField(),
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
      "tenantId", TENANT_ID,
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
      "tenantId", TENANT_ID,
      "title", jsonArray("instance title"),
      "language", "eng",
      "multilang_value", jsonObject("eng", "some value", "src", "some value"),
      "plain_multilang_value", "some value",
      "bool", true,
      "number", 123,
      "numbers", jsonArray(1, 2, 3, 4),
      "metadata", jsonObject("createdAt", "12-01-01T12:03:12Z"));
  }

  @SneakyThrows
  private static Optional<SearchDocumentBody> expectedSearchDocument(ResourceEvent event, ObjectNode expectedJson) {
    return Optional.of(SearchDocumentBody.of(new BytesArray(SMILE_MAPPER.writeValueAsBytes(expectedJson)),
      IndexingDataFormat.SMILE, event, INDEX));
  }

  private SearchConfigurationProperties getSearchConfigurationProperties() {
    var indexSettings = new IndexingSettings();
    indexSettings.setDataFormat(IndexingDataFormat.SMILE);
    var searchConfigurationProperties = new SearchConfigurationProperties();
    searchConfigurationProperties.setIndexing(indexSettings);
    return searchConfigurationProperties;
  }
}
