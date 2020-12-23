package org.folio.search.service;

import static com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.JsonUtils.arrayNode;
import static org.folio.search.utils.JsonUtils.jsonObject;
import static org.folio.search.utils.TestConstants.INDEX_NAME;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.asJsonString;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.multilangField;
import static org.folio.search.utils.TestUtils.objectField;
import static org.folio.search.utils.TestUtils.plainField;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.resourceDescription;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.ParseContext;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.folio.search.model.ResourceEventBody;
import org.folio.search.model.SearchDocumentBody;
import org.folio.search.model.metadata.FieldDescription;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.folio.search.utils.JsonConverter;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@UnitTest
@ExtendWith(MockitoExtension.class)
class SearchDocumentMapperTest {

  @Spy private final ObjectMapper objectMapper = new ObjectMapper()
      .configure(ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
  @Spy private final JsonConverter jsonConverter = new JsonConverter(objectMapper);

  @Spy private final ParseContext parseContext = JsonPath.using(Configuration.builder()
      .jsonProvider(new JacksonJsonProvider(objectMapper))
      .mappingProvider(new JacksonMappingProvider(objectMapper))
      .options(EnumSet.noneOf(Option.class))
      .build());

  @Mock private ResourceDescriptionService descriptionService;

  @InjectMocks private SearchDocumentMapper documentMapper;

  @BeforeAll
  static void beforeAll() {
    LoggerContext logContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    ch.qos.logback.classic.Logger log = logContext.getLogger(
        "com.jayway.jsonpath.internal.path.CompiledPath");
    log.setLevel(Level.INFO);
  }

  @Test
  void convertSingle_positive() {
    var id = randomId();
    var jsonBody = getResourceTestData(id);
    var eventBody = ResourceEventBody.of("CREATE", TENANT_ID, RESOURCE_NAME, jsonBody);
    var resourceDescription = resourceDescription(getFieldDescriptions());
    var languageSources = List.of("$.language");

    when(descriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription);
    when(descriptionService.getLanguageSourcePaths(RESOURCE_NAME)).thenReturn(languageSources);
    when(descriptionService.isSupportedLanguage("eng")).thenReturn(true);

    var actual = documentMapper.convert(eventBody);

    var expectedJson = asJsonString(getExpectedDocument(id));
    assertThat(actual).isPresent().get()
        .isEqualTo(SearchDocumentBody.of(id, TENANT_ID, INDEX_NAME, expectedJson));

    verify(parseContext).parse(jsonConverter.toJson(jsonBody));
  }

  @Test
  void convertMultiple_positive() {
    var id = randomId();
    var jsonBody = getResourceTestData(id);
    var eventBody = ResourceEventBody.of("CREATE", TENANT_ID, RESOURCE_NAME, jsonBody);
    var resourceDescription = resourceDescription(getFieldDescriptions());
    var languageSources = List.of("$.language");

    when(descriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription);
    when(descriptionService.getLanguageSourcePaths(RESOURCE_NAME)).thenReturn(languageSources);
    when(descriptionService.isSupportedLanguage("eng")).thenReturn(true);

    var actual = documentMapper.convert(List.of(eventBody));

    var expectedJson = asJsonString(getExpectedDocument(id));
    assertThat(actual).isEqualTo(List.of(
        SearchDocumentBody.of(id, TENANT_ID, INDEX_NAME, expectedJson)));
    verify(parseContext).parse(jsonConverter.toJson(jsonBody));
  }

  @Test
  void convertSingle_negative_pathNotFound() {
    var id = randomId();
    var jsonBody = jsonObject("id", id);
    var eventBody = ResourceEventBody.of("CREATE", TENANT_ID, RESOURCE_NAME, jsonBody);
    var resourceDescription = resourceDescription(mapOf(
        "id", plainField("keyword", "$.id"),
        "title", plainField("keyword", "$.title")));

    when(descriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription);
    when(descriptionService.getLanguageSourcePaths(RESOURCE_NAME)).thenReturn(emptyList());

    var actual = documentMapper.convert(eventBody);

    var expectedJson = asJsonString(jsonObject("id", id));
    assertThat(actual).isPresent().get()
        .isEqualTo(SearchDocumentBody.of(id, TENANT_ID, INDEX_NAME, expectedJson));

    verify(parseContext).parse(jsonConverter.toJson(jsonBody));
  }

  @Test
  void convertSingle_negative_dataIsNull() {
    var eventBody = ResourceEventBody.of("CREATE", TENANT_ID, RESOURCE_NAME, null);
    var actual = documentMapper.convert(eventBody);
    assertThat(actual).isNotPresent();
  }

  private static Map<String, FieldDescription> getFieldDescriptions() {
    return mapOf(
        "id", plainField("keyword", "$.id"),
        "title", plainField("keyword", "$.title"),
        "language", plainField("keyword", "$.language"),
        "multilang_value", multilangField("$.multilang_value"),
        "bool", plainField("boolean", "$.bool"),
        "number", plainField("numeric", "$.number"),
        "numbers", plainField("numeric", "$.numbers"),
        "ignored_field", plainField("none"),
        "metadata", objectField(mapOf(
            "createdAt", plainField("keyword", "$.metadata.createdAt"))));
  }

  private static ObjectNode getResourceTestData(String id) {
    return jsonObject(
        "id", id,
        "title", arrayNode("instance title"),
        "language", "eng",
        "multilang_value", "some value",
        "bool", true,
        "number", 123,
        "numbers", arrayNode(1, 2, 3, 4),
        "ignored_field", "ignored value",
        "metadata", jsonObject(
            "createdAt", "12-01-01T12:03:12Z"));
  }

  private static ObjectNode getExpectedDocument(String id) {
    return jsonObject(
        "id", id,
        "title", "instance title",
        "language", "eng",
        "multilang_value", jsonObject(
            "eng", "some value",
            "source", "some value"),
        "bool", true,
        "number", 123,
        "numbers", arrayNode(1, 2, 3, 4),
        "metadata", jsonObject(
            "createdAt", "12-01-01T12:03:12Z"));
  }
}
