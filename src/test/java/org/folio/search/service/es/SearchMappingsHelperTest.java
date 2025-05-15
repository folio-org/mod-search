package org.folio.search.service.es;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.model.metadata.PlainFieldDescription.MULTILANG_FIELD_TYPE;
import static org.folio.search.model.metadata.PlainFieldDescription.PLAIN_FULLTEXT_FIELD_TYPE;
import static org.folio.search.model.metadata.PlainFieldDescription.STANDARD_FIELD_TYPE;
import static org.folio.search.utils.SearchUtils.KEYWORD_FIELD_INDEX;
import static org.folio.support.utils.JsonTestUtils.OBJECT_MAPPER;
import static org.folio.support.utils.JsonTestUtils.asJsonString;
import static org.folio.support.utils.JsonTestUtils.jsonArray;
import static org.folio.support.utils.JsonTestUtils.jsonObject;
import static org.folio.support.utils.TestUtils.languageConfig;
import static org.folio.support.utils.TestUtils.languageConfigs;
import static org.folio.support.utils.TestUtils.mapOf;
import static org.folio.support.utils.TestUtils.multilangField;
import static org.folio.support.utils.TestUtils.objectField;
import static org.folio.support.utils.TestUtils.plainField;
import static org.folio.support.utils.TestUtils.searchField;
import static org.folio.support.utils.TestUtils.standardField;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import org.folio.search.domain.dto.LanguageConfig;
import org.folio.search.domain.dto.LanguageConfigs;
import org.folio.search.exception.ResourceDescriptionException;
import org.folio.search.model.metadata.ResourceDescription;
import org.folio.search.model.metadata.SearchFieldType;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.consortium.LanguageConfigServiceDecorator;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.testing.type.UnitTest;
import org.folio.support.utils.TestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class SearchMappingsHelperTest {

  @InjectMocks
  private SearchMappingsHelper mappingsHelper;
  @Mock
  private SearchFieldProvider searchFieldProvider;
  @Mock
  private LanguageConfigServiceDecorator languageConfigService;
  @Mock
  private ResourceDescriptionService resourceDescriptionService;
  @Spy
  private final JsonConverter jsonConverter = new JsonConverter(OBJECT_MAPPER);

  @Test
  void getMappings_positive() {
    var keywordType = fieldType(jsonObject("type", KEYWORD_FIELD_INDEX));
    var dateType = fieldType(jsonObject("type", "date", "format", "epoch_millis"));
    var multilangType = multilangFieldType();
    var standardType = standardFieldType();
    var plainFulltextType = plainFulltextFieldType();

    when(resourceDescriptionService.get(ResourceType.UNKNOWN)).thenReturn(resourceDescription());
    when(languageConfigService.getAll()).thenReturn(getSupportedLanguages());

    doReturn(keywordType).when(searchFieldProvider).getSearchFieldType(KEYWORD_FIELD_INDEX);
    doReturn(multilangType).when(searchFieldProvider).getSearchFieldType(MULTILANG_FIELD_TYPE);
    doReturn(plainFulltextType).when(searchFieldProvider).getSearchFieldType(PLAIN_FULLTEXT_FIELD_TYPE);
    doReturn(dateType).when(searchFieldProvider).getSearchFieldType("date");
    doReturn(standardType).when(searchFieldProvider).getSearchFieldType(STANDARD_FIELD_TYPE);

    var actual = mappingsHelper.getMappings(ResourceType.UNKNOWN);

    assertThat(actual).isEqualTo(asJsonString(jsonObject(
      "date_detection", false,
      "numeric_detection", false,
      "properties", jsonObject(
        "id", keywordType.getMapping(),
        "title", multilangType.getMapping(),
        "plain_title", plainFulltextType.getMapping(),
        "contributors", jsonObject(
          "properties", jsonObject(
            "name", standardType.getMapping(),
            "plain_name", plainFulltextType.getMapping())),
        "subtitle", jsonObject("type", "text"),
        "standardNotes", standardType.getMapping(),
        "isbn", standardType.getMapping(),
        "issn", jsonObject("type", KEYWORD_FIELD_INDEX, "normalizer", "lowercase_normalizer"),
        "metadata", jsonObject("properties", jsonObject("createdDate", dateType.getMapping())),
        "identifiers", keywordType.getMapping())
    )));
    verify(jsonConverter).toJson(anyMap());
  }

  @Test
  void getMappings_positive_resourceWithIndexMappings() {
    var keywordType = fieldType(jsonObject("type", KEYWORD_FIELD_INDEX));
    var resourceDescription = TestUtils.resourceDescription(mapOf(
      "id", plainField(KEYWORD_FIELD_INDEX, jsonObject("copy_to", jsonArray("id_copy")))));
    var idCopyMappings = jsonObject("type", KEYWORD_FIELD_INDEX, "normalizer", "lowercase");
    resourceDescription.setIndexMappings(mapOf("id_copy", idCopyMappings));

    when(resourceDescriptionService.get(ResourceType.UNKNOWN)).thenReturn(resourceDescription);
    when(searchFieldProvider.getSearchFieldType(KEYWORD_FIELD_INDEX)).thenReturn(keywordType);

    var actual = mappingsHelper.getMappings(ResourceType.UNKNOWN);
    assertThat(actual).isEqualTo(asJsonString(jsonObject(
      "date_detection", false,
      "numeric_detection", false,
      "properties", jsonObject(
        "id", jsonObject("type", KEYWORD_FIELD_INDEX, "copy_to", jsonArray("id_copy")),
        "id_copy", idCopyMappings
      ))));
  }

  @Test
  void getMappings_positive_resourceWithMultilangIndexMappings() {
    var resourceDescription = TestUtils.resourceDescription(mapOf(
      "id", plainField(KEYWORD_FIELD_INDEX),
      "alternativeTitle", multilangField(),
      "title", plainField(MULTILANG_FIELD_TYPE, jsonObject("copy_to", jsonArray("sort_title")))));
    var sortTitleMappings = jsonObject("type", KEYWORD_FIELD_INDEX, "analyzer", "lowercase_keyword");
    resourceDescription.setIndexMappings(mapOf("sort_title", sortTitleMappings));

    var multilangType = multilangFieldType();
    var multilangPlainType = plainFulltextFieldType();
    var keywordType = fieldType(jsonObject("type", KEYWORD_FIELD_INDEX));

    when(resourceDescriptionService.get(ResourceType.UNKNOWN)).thenReturn(resourceDescription);
    when(languageConfigService.getAll()).thenReturn(getSupportedLanguages());
    doReturn(multilangType).when(searchFieldProvider).getSearchFieldType(MULTILANG_FIELD_TYPE);
    doReturn(multilangPlainType).when(searchFieldProvider).getSearchFieldType(PLAIN_FULLTEXT_FIELD_TYPE);
    doReturn(keywordType).when(searchFieldProvider).getSearchFieldType(KEYWORD_FIELD_INDEX);

    var actual = mappingsHelper.getMappings(ResourceType.UNKNOWN);
    assertThat(actual).isEqualTo(asJsonString(jsonObject(
      "date_detection", false,
      "numeric_detection", false,
      "properties", jsonObject(
        "id", keywordType.getMapping(),
        "alternativeTitle", multilangType.getMapping(),
        "plain_alternativeTitle", plainFulltextFieldType().getMapping(),
        "title", multilangType.getMapping(),
        "plain_title", jsonObject(
          "type", "keyword",
          "normalizer", "keyword_lowercase",
          "copy_to", jsonArray("sort_title")),
        "sort_title", sortTitleMappings
      ))));
  }

  @Test
  void shouldRemoveUnsupportedLanguages() {
    var resourceDescription = TestUtils.resourceDescription(mapOf("title", plainField(MULTILANG_FIELD_TYPE)));
    var multilangPlainType = plainFulltextFieldType();

    when(resourceDescriptionService.get(ResourceType.UNKNOWN)).thenReturn(resourceDescription);
    when(languageConfigService.getAll()).thenReturn(languageConfigs(List.of(languageConfig("eng"))));
    doReturn(multilangFieldType()).when(searchFieldProvider).getSearchFieldType(MULTILANG_FIELD_TYPE);
    doReturn(multilangPlainType).when(searchFieldProvider).getSearchFieldType(PLAIN_FULLTEXT_FIELD_TYPE);

    var actual = mappingsHelper.getMappings(ResourceType.UNKNOWN);
    assertThat(actual).isEqualTo(asJsonString(jsonObject(
      "date_detection", false,
      "numeric_detection", false,
      "properties", jsonObject(
        "title", jsonObject(
          "properties", jsonObject(
            "eng", jsonObject("type", "text", "analyzer", "english"),
            "src", jsonObject("type", "text", "analyzer", "source_analyzer")
          )),
        "plain_title", multilangPlainType.getMapping()
      ))));
  }

  @Test
  void getMappings_shouldUpdateAnalyzerForKoreanLanguage() {
    var resourceDescription = TestUtils.resourceDescription(mapOf("title", multilangField()));
    var multilangPlainType = plainFulltextFieldType();

    when(resourceDescriptionService.get(ResourceType.UNKNOWN)).thenReturn(resourceDescription);
    doReturn(multilangFieldType()).when(searchFieldProvider).getSearchFieldType(MULTILANG_FIELD_TYPE);
    doReturn(multilangPlainType).when(searchFieldProvider).getSearchFieldType(PLAIN_FULLTEXT_FIELD_TYPE);
    when(languageConfigService.getAll()).thenReturn(languageConfigs(List.of(languageConfig("eng", "custom_eng"))));

    var actual = mappingsHelper.getMappings(ResourceType.UNKNOWN);
    assertThat(actual).isEqualTo(asJsonString(jsonObject(
      "date_detection", false,
      "numeric_detection", false,
      "properties", jsonObject(
        "title", jsonObject(
          "properties", jsonObject(
            "eng", jsonObject("type", "text", "analyzer", "custom_eng"),
            "src", jsonObject("type", "text", "analyzer", "source_analyzer")
          )),
        "plain_title", multilangPlainType.getMapping()
      ))));
  }

  @Test
  void getMappings_positive_nullFieldType() {
    var resourceDescription = TestUtils.resourceDescription(mapOf("title", multilangField()));

    when(resourceDescriptionService.get(ResourceType.UNKNOWN)).thenReturn(resourceDescription);
    doReturn(null).when(searchFieldProvider).getSearchFieldType(MULTILANG_FIELD_TYPE);

    assertThatThrownBy(() -> mappingsHelper.getMappings(ResourceType.UNKNOWN))
      .isInstanceOf(ResourceDescriptionException.class)
      .hasMessage("Failed to find related mappings for index type: multilang");
  }

  @Test
  void getMappings_positive_nullMappingsForMultilangField() {
    var resourceDescription = TestUtils.resourceDescription(mapOf("title", multilangField()));

    when(resourceDescriptionService.get(ResourceType.UNKNOWN)).thenReturn(resourceDescription);
    doReturn(fieldType(null)).when(searchFieldProvider).getSearchFieldType(MULTILANG_FIELD_TYPE);

    assertThatThrownBy(() -> mappingsHelper.getMappings(ResourceType.UNKNOWN))
      .isInstanceOf(ResourceDescriptionException.class)
      .hasMessage("Failed to find related mappings for index type: multilang");
  }

  private static ResourceDescription resourceDescription() {
    var resourceDescription = TestUtils.resourceDescription(mapOf(
      "id", plainField(KEYWORD_FIELD_INDEX),
      "title", plainField(MULTILANG_FIELD_TYPE),
      "contributors", objectField(mapOf("name", standardField())),
      "subtitle", plainField(null, jsonObject("type", "text")),
      "not_indexed_field1", plainField("none"),
      "not_indexed_field2", plainField(null),
      "standardNotes", standardField(false),
      "isbn", standardField(false),
      "issn", plainField(KEYWORD_FIELD_INDEX, jsonObject("normalizer", "lowercase_normalizer")),
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

  private static SearchFieldType standardFieldType() {
    return fieldType(jsonObject("type", "text", "analyzer", "source_analyzer"));
  }

  private static SearchFieldType plainFulltextFieldType() {
    return fieldType(jsonObject("type", "keyword", "normalizer", "keyword_lowercase"));
  }

  private static LanguageConfigs getSupportedLanguages() {
    var languageConfigs = new ArrayList<LanguageConfig>();
    multilangFieldType().getMapping().path("properties").fieldNames()
      .forEachRemaining(name -> languageConfigs.add(languageConfig(name)));

    return languageConfigs(languageConfigs);
  }

  private static SearchFieldType fieldType(ObjectNode mappings) {
    return SearchFieldType.of(mappings);
  }
}
