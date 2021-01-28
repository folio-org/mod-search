package org.folio.search.service.converter;

import static com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.folio.search.domain.dto.ResourceEventBody;
import org.folio.search.model.SearchDocumentBody;
import org.folio.search.model.metadata.FieldDescription;
import org.folio.search.service.LanguageConfigService;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.folio.search.utils.JsonConverter;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class SearchDocumentConverterTest {

  @InjectMocks
  @Spy
  private SearchDocumentConverter documentMapper;
  @Mock private ResourceDescriptionService descriptionService;
  @Mock
  private LanguageConfigService languageConfigService;
  @Spy private final ObjectMapper objectMapper = new ObjectMapper().configure(ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
  @Spy private final JsonConverter jsonConverter = new JsonConverter(objectMapper);

  @Test
  void convertSingle_positive() {
    var id = randomId();
    var jsonBody = getResourceTestData(id);
    var eventBody = eventBody(RESOURCE_NAME, jsonBody);
    var resourceDescription = resourceDescription(getDescriptionFields(), List.of("$.language"));

    when(descriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription);
    when(languageConfigService.getAllSupportedLanguageCodes()).thenReturn(Set.of("eng"));

    var actual = documentMapper.convert(List.of(eventBody));
    var expectedJson = asJsonString(getExpectedDocument(id));

    assertThat(actual, contains(SearchDocumentBody.of(id, TENANT_ID, INDEX_NAME, expectedJson)));
    verify(jsonConverter).toJson(anyMap());
  }

  @Test
  void convertMultiple_positive() {
    var id = randomId();
    var resourceData = getResourceTestData(id);
    var eventBody = eventBody(RESOURCE_NAME, resourceData);
    var resourceDescription = resourceDescription(getDescriptionFields(), List.of("$.language"));

    when(descriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription);
    when(languageConfigService.getAllSupportedLanguageCodes()).thenReturn(Set.of("eng"));

    var actual = documentMapper.convert(List.of(eventBody));

    var expectedJson = asJsonString(getExpectedDocument(id));
    assertThat(actual, contains(SearchDocumentBody.of(id, TENANT_ID, INDEX_NAME, expectedJson)));
  }

  @Test
  void convertSingle_negative_pathNotFound() {
    var id = randomId();
    var eventBody = eventBody(RESOURCE_NAME, mapOf("id", id));
    var resourceDescription = resourceDescription(mapOf(
      "id", plainField("keyword"),
      "title", plainField("keyword")));

    when(descriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription);

    var actual = documentMapper.convert(List.of(eventBody));
    var expectedJson = asJsonString(jsonObject("id", id));

    assertThat(actual, contains(SearchDocumentBody.of(id, TENANT_ID, INDEX_NAME, expectedJson)));
  }

  @Test
  void convertSingle_negative_emptyTitle() {
    var id = randomId();
    var eventBody = eventBody(RESOURCE_NAME, mapOf("id", id, "title", ""));
    var resourceDescription = resourceDescription(
      mapOf("id", plainField("keyword"), "title", plainField("keyword")));

    when(descriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription);

    var actual = documentMapper.convert(List.of(eventBody));
    var expectedJson = asJsonString(jsonObject("id", id, "title", ""));

    assertThat(actual, contains(SearchDocumentBody.of(id, TENANT_ID, INDEX_NAME, expectedJson)));
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
    when(languageConfigService.getAllSupportedLanguageCodes()).thenReturn(Set.of("eng"));

    var actual = documentMapper.convert(List.of(eventBody));
    var expectedJson = asJsonString(jsonObject("id", id, "title", jsonObject("eng", "val", "src", "val")));

    assertThat(actual, contains(SearchDocumentBody.of(id, TENANT_ID, INDEX_NAME, expectedJson)));
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

    var actual = documentMapper.convert(List.of(resourceEventBody));
    var expectedJson = asJsonString(jsonObject(
      "id", id, "identifiers", jsonArray(jsonObject("value", "test-isbn"), jsonObject("value", "test-issn"))));

    assertThat(actual, contains(SearchDocumentBody.of(id, TENANT_ID, INDEX_NAME, expectedJson)));
  }

  @Test
  void convertSingle_positive_multiLanguageValue() {
    var id = randomId();
    var resourceDescription = resourceDescription(mapOf(
      "id", keywordField(), "alternativeTitle", objectField(mapOf("value", multilangField()))));
    var resourceEventBody = eventBody(RESOURCE_NAME, mapOf("id", id,
      "alternativeTitle", List.of(mapOf("value", "title1"), mapOf("value", null), emptyMap(), "title3")));

    when(descriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription);
    var actual = documentMapper.convert(List.of(resourceEventBody));
    var expectedJson = asJsonString(jsonObject(
      "id", id, "alternativeTitle", jsonArray(jsonObject("value", jsonObject("src", "title1")))));
    assertThat(actual, contains(SearchDocumentBody.of(id, TENANT_ID, INDEX_NAME, expectedJson)));
  }

  @Test
  void convertSingle_negative_dataIsNull() {
    var eventBody = eventBody(RESOURCE_NAME, null);
    var actual = documentMapper.convert(List.of(eventBody));
    assertThat(actual, empty());
  }

  @Test
  void shouldGroupEventsByTenant() {
    var resourceDescription = resourceDescription(getDescriptionFields(), List.of("$.language"));

    when(descriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription);
    when(languageConfigService.getAllSupportedLanguageCodes()).thenReturn(Set.of("eng"));

    var event1ForTenantOne = simpleEventForTenant("tenant_one");
    var event2ForTenantOne = simpleEventForTenant("tenant_one");
    var eventForTenantTwo = simpleEventForTenant("tenant_two");
    var eventForTenantThree = simpleEventForTenant("tenant_three");

    documentMapper.convert(List.of(event1ForTenantOne, eventForTenantThree, eventForTenantTwo,
      event2ForTenantOne));

    @SuppressWarnings("unchecked")
    final ArgumentCaptor<List<ResourceEventBody>> arguments = ArgumentCaptor.forClass(List.class);
    verify(documentMapper, times(3)).convert(any(), arguments.capture());

    verify(documentMapper, times(1)).beginFolioExecutionContext("tenant_one");
    verify(documentMapper, times(1)).beginFolioExecutionContext("tenant_two");
    verify(documentMapper, times(1)).beginFolioExecutionContext("tenant_three");
    verify(documentMapper, times(3)).endFolioExecutionContext();

    assertThat(getCapturedEventsForTenant(arguments, "tenant_one"),
      containsInAnyOrder(event1ForTenantOne, event2ForTenantOne));
    assertThat(getCapturedEventsForTenant(arguments, "tenant_two"),
      contains(eventForTenantTwo));
    assertThat(getCapturedEventsForTenant(arguments, "tenant_three"),
      contains(eventForTenantThree));
  }

  @Test
  void shouldFinalizeContextEventIfFailureOccurred() {
    when(languageConfigService.getAllSupportedLanguageCodes()).thenThrow(new RuntimeException());

    handleEventsIgnoreException(simpleEventForTenant(TENANT_ID));

    verify(documentMapper, times(1)).beginFolioExecutionContext(TENANT_ID);
    verify(documentMapper, times(1)).endFolioExecutionContext();
  }

  private void handleEventsIgnoreException(ResourceEventBody event) {
    try {
      documentMapper.convert(List.of(event));
    } catch (Exception ex) {
      // nothing to do - ignoring
    }
  }

  private List<ResourceEventBody> getCapturedEventsForTenant(
    ArgumentCaptor<List<ResourceEventBody>> arguments, String tenant) {

    return arguments.getAllValues().stream()
      .filter(list -> list.get(0).getTenant().equals(tenant))
      .findFirst()
      .orElse(emptyList());
  }

  private ResourceEventBody simpleEventForTenant(String tenant) {
    return eventBody(RESOURCE_NAME, Map.of("id", randomId()))
      .tenant(tenant);
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
