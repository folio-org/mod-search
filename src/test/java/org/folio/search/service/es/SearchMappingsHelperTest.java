package org.folio.search.service.es;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.JsonUtils.jsonArray;
import static org.folio.search.utils.JsonUtils.jsonObject;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestUtils.asJsonString;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.objectField;
import static org.folio.search.utils.TestUtils.plainField;
import static org.folio.search.utils.TestUtils.searchField;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.folio.search.model.metadata.PlainFieldDescription;
import org.folio.search.model.metadata.ResourceDescription;
import org.folio.search.model.metadata.SearchFieldType;
import org.folio.search.service.LanguageConfigService;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.folio.search.utils.JsonConverter;
import org.folio.search.utils.TestUtils;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class SearchMappingsHelperTest {

  private static final String MULTILANG_TYPE = "multilang";
  private static final String KEYWORD_TYPE = "keyword";

  @InjectMocks private SearchMappingsHelper mappingsHelper;

  @Spy private final ObjectMapper objectMapper = new ObjectMapper();
  @Spy private final JsonConverter jsonConverter = new JsonConverter(objectMapper);

  @Mock private ResourceDescriptionService resourceDescriptionService;
  @Mock private SearchFieldProvider searchFieldProvider;
  @Mock private LanguageConfigService languageConfigService;

  @Test
  void getMappings_positive() {
    var keywordType = fieldType(jsonObject("type", KEYWORD_TYPE));
    var dateType = fieldType(jsonObject("type", "date", "format", "epoch_millis"));
    var multilangType = multilangFieldType();

    when(resourceDescriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription());
    doReturn(keywordType).when(searchFieldProvider).getSearchFieldType(KEYWORD_TYPE);
    doReturn(multilangFieldType()).when(searchFieldProvider).getSearchFieldType(MULTILANG_TYPE);
    doReturn(dateType).when(searchFieldProvider).getSearchFieldType("date");
    when(languageConfigService.getAllLanguageCodes()).thenReturn(getSupportedLanguages());

    var actual = mappingsHelper.getMappings(RESOURCE_NAME);

    assertThat(actual).isEqualTo(asJsonString(jsonObject(
      "date_detection", false,
      "numeric_detection", false,
      "_routing", jsonObject("required", true),
      "properties", jsonObject(
        "id", keywordType.getMapping(),
        "title", multilangType.getMapping(),
        "subtitle", jsonObject("type", "text"),
        "isbn", jsonObject("type", KEYWORD_TYPE, "normalizer", "lowercase_normalizer"),
        "metadata", jsonObject("properties", jsonObject("createdDate", dateType.getMapping())),
        "identifiers", keywordType.getMapping()
      ))));
    verify(jsonConverter).toJson(anyMap());
  }

  @Test
  void getMappings_positive_resourceWithIndexMappings() {
    var keywordType = fieldType(jsonObject("type", KEYWORD_TYPE));
    var resourceDescription = TestUtils.resourceDescription(mapOf(
      "id", plainField(KEYWORD_TYPE, jsonObject("copy_to", jsonArray("id_copy")))));
    var idCopyMappings = jsonObject("type", KEYWORD_TYPE, "normalizer", "lowercase");
    resourceDescription.setIndexMappings(mapOf("id_copy", idCopyMappings));

    when(resourceDescriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription);
    when(searchFieldProvider.getSearchFieldType(KEYWORD_TYPE)).thenReturn(keywordType);

    var actual = mappingsHelper.getMappings(RESOURCE_NAME);
    assertThat(actual).isEqualTo(asJsonString(jsonObject(
      "date_detection", false,
      "numeric_detection", false,
      "_routing", jsonObject("required", true),
      "properties", jsonObject(
        "id", jsonObject("type", KEYWORD_TYPE, "copy_to", jsonArray("id_copy")),
        "id_copy", idCopyMappings
      ))));
  }

  @Test
  void getMappings_positive_resourceWithMultilangIndexMappings() {
    var resourceDescription = TestUtils.resourceDescription(mapOf(
      "id", plainField(KEYWORD_TYPE),
      "title", plainField(MULTILANG_TYPE, jsonObject("copy_to", jsonArray("sort_title")))));
    var sortTitleMappings = jsonObject("type", KEYWORD_TYPE, "analyzer", "lowercase_keyword");
    resourceDescription.setIndexMappings(mapOf("sort_title", sortTitleMappings));

    var multilangType = multilangFieldType();
    var keywordType = fieldType(jsonObject("type", KEYWORD_TYPE));

    when(resourceDescriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription);
    doReturn(multilangType).when(searchFieldProvider).getSearchFieldType(MULTILANG_TYPE);
    when(searchFieldProvider.getSearchFieldType(KEYWORD_TYPE)).thenReturn(keywordType);
    when(languageConfigService.getAllLanguageCodes()).thenReturn(getSupportedLanguages());

    var actual = mappingsHelper.getMappings(RESOURCE_NAME);
    assertThat(actual).isEqualTo(asJsonString(jsonObject(
      "date_detection", false,
      "numeric_detection", false,
      "_routing", jsonObject("required", true),
      "properties", jsonObject(
        "id", keywordType.getMapping(),
        "title", jsonObject(
          "properties", jsonObject(
            "eng", jsonObject("type", "text", "analyzer", "english"),
            "spa", jsonObject("type", "text", "analyzer", "spanish"),
            "src", jsonObject("type", "text", "analyzer", "source_analyzer", "copy_to", jsonArray("sort_title"))
          )),
        "sort_title", sortTitleMappings
      ))));
  }

  @Test
  void shouldRemoveUnsupportedLanguages() {
    var resourceDescription = TestUtils.resourceDescription(mapOf("title", plainField(MULTILANG_TYPE)));
    var multilangType = multilangFieldType();

    when(resourceDescriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription);
    doReturn(multilangType).when(searchFieldProvider).getSearchFieldType(MULTILANG_TYPE);
    when(languageConfigService.getAllLanguageCodes()).thenReturn(Set.of("eng"));

    var actual = mappingsHelper.getMappings(RESOURCE_NAME);
    assertThat(actual).isEqualTo(asJsonString(jsonObject(
      "date_detection", false,
      "numeric_detection", false,
      "_routing", jsonObject("required", true),
      "properties", jsonObject(
        "title", jsonObject(
          "properties", jsonObject(
            "eng", jsonObject("type", "text", "analyzer", "english"),
            "src", jsonObject("type", "text", "analyzer", "source_analyzer")
          ))
      ))));
  }

  private static ResourceDescription resourceDescription() {
    var resourceDescription = TestUtils.resourceDescription(mapOf(
      "id", plainField(KEYWORD_TYPE),
      "issn", identifiersGroup(),
      "title", plainField(MULTILANG_TYPE),
      "subtitle", plainField(null, jsonObject("type", "text")),
      "not_indexed_field1", plainField("none"),
      "not_indexed_field2", plainField(null),
      "isbn", plainField(KEYWORD_TYPE, jsonObject("normalizer", "lowercase_normalizer")),
      "metadata", objectField(mapOf(
        "createdDate", plainField("date")
      ))));
    resourceDescription.setSearchFields(mapOf("identifiers", searchField("isbn_identifier")));
    return resourceDescription;
  }

  private static SearchFieldType multilangFieldType() {
    return fieldType(jsonObject(
      "properties", jsonObject(
        "eng", jsonObject("type", "text", "analyzer", "english"),
        "spa", jsonObject("type", "text", "analyzer", "spanish"),
        "src", jsonObject("type", "text", "analyzer", "source_analyzer")
      )));
  }

  private static Set<String> getSupportedLanguages() {
    var languages = new HashSet<String>();
    multilangFieldType().getMapping().path("properties")
      .fieldNames().forEachRemaining(languages::add);

    return languages;
  }

  private static PlainFieldDescription identifiersGroup() {
    var groupField = plainField(KEYWORD_TYPE);
    groupField.setGroup(List.of("identifiers"));
    return groupField;
  }

  private static SearchFieldType fieldType(ObjectNode mappings) {
    return SearchFieldType.of(mappings);
  }
}
