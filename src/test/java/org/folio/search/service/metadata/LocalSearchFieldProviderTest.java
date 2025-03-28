package org.folio.search.service.metadata;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.model.types.ResourceType.INSTANCE;
import static org.folio.search.model.types.ResourceType.UNKNOWN;
import static org.folio.search.model.types.ResponseGroupType.SEARCH;
import static org.folio.search.model.types.SearchType.FACET;
import static org.folio.search.model.types.SearchType.FILTER;
import static org.folio.support.utils.JsonTestUtils.jsonObject;
import static org.folio.support.utils.TestUtils.mapOf;
import static org.folio.support.utils.TestUtils.multilangField;
import static org.folio.support.utils.TestUtils.objectField;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.folio.search.cql.SearchFieldModifier;
import org.folio.search.exception.ResourceDescriptionException;
import org.folio.search.model.metadata.FieldDescription;
import org.folio.search.model.metadata.PlainFieldDescription;
import org.folio.search.model.metadata.PostProcessResourceDescriptionConverter;
import org.folio.search.model.metadata.ResourceDescription;
import org.folio.search.model.metadata.SearchFieldDescriptor;
import org.folio.search.model.metadata.SearchFieldType;
import org.folio.search.model.types.ResponseGroupType;
import org.folio.search.model.types.SearchType;
import org.folio.spring.testing.type.UnitTest;
import org.folio.support.utils.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class LocalSearchFieldProviderTest {

  private static final String TITLE_SEARCH_TYPE = "title";
  private final PostProcessResourceDescriptionConverter converter = new PostProcessResourceDescriptionConverter();
  @Mock
  private MetadataResourceProvider metadataResourceProvider;

  @BeforeEach
  void setUp() {
    getSearchFieldProvider();
  }

  @Test
  void getSearchFieldType_positive() {
    var actual = getSearchFieldProvider().getSearchFieldType("keyword");
    assertThat(actual).isEqualTo(new SearchFieldType());
  }

  @Test
  void getSearchFieldType_negative() {
    var searchFieldProvider = getSearchFieldProvider();
    assertThatThrownBy(() -> searchFieldProvider.getSearchFieldType(null))
      .isInstanceOf(ResourceDescriptionException.class)
      .hasMessageContaining("Failed to find search field type [fieldType: null]");
  }

  @Test
  void getFieldByInventorySearchType_positive() {
    var fields = getSearchFieldProvider().getFields(INSTANCE, TITLE_SEARCH_TYPE);
    assertThat(fields).containsExactlyInAnyOrder(
      "title1.*", "title2.sub1", "title2.sub2.*", "title2.sub3.sub4", "search1");
  }

  @Test
  void getFieldByInventorySearchType_positive_nonExistingResource() {
    var fields = getSearchFieldProvider().getFields(UNKNOWN, TITLE_SEARCH_TYPE);
    assertThat(fields).isEmpty();
  }

  @Test
  void getSourceFields_positive() {
    var actual = getSearchFieldProvider().getSourceFields(INSTANCE, SEARCH);
    assertThat(actual).containsExactlyInAnyOrder("id", "plain_title1", "title2.sub1", "contributors.plain_name",
      "title2.sub3.plain_sub5", "source");
  }

  @Test
  void getSourceFields_positive_nonExistingResource() {
    var actual = getSearchFieldProvider().getSourceFields(UNKNOWN, SEARCH);
    assertThat(actual).isNull();
  }

  @Test
  void getModifiedField_positive() {
    var fieldName = "field";
    var modifiedField = "modifiedField";
    var modifierName = "modifier";

    var resourceDescription = resourceDescription();
    resourceDescription.setSearchFieldModifiers(List.of(modifierName));
    var modifiersMap = Map.of(modifierName, getModifier(modifiedField));
    var searchFieldProvider = new LocalSearchFieldProvider(metadataResourceProvider, modifiersMap);
    when(metadataResourceProvider.getResourceDescription(INSTANCE)).thenReturn(Optional.of(resourceDescription));

    var actual = searchFieldProvider.getModifiedField(fieldName, INSTANCE);
    assertThat(actual).isEqualTo(modifiedField);
  }

  @Test
  void getModifiedField_positive_shouldNotModify() {
    var fieldName = "field";
    var searchFieldProvider = new LocalSearchFieldProvider(metadataResourceProvider, emptyMap());
    var actual = searchFieldProvider.getModifiedField(fieldName, INSTANCE);
    assertThat(actual).isEqualTo(fieldName);
  }

  @MethodSource("getPlainFieldsByPathDataProvider")
  @ParameterizedTest(name = "[{index}] path={0}")
  void getPlainFieldByPath_positive_parameterized(String path, FieldDescription expected) {
    when(metadataResourceProvider.getResourceDescription(INSTANCE)).thenReturn(Optional.of(resourceDescription()));
    var actual = getSearchFieldProvider().getPlainFieldByPath(INSTANCE, path);
    assertThat(actual).isEqualTo(Optional.ofNullable(expected));
  }

  @Test
  void getPlainFieldByPath_positive() {
    when(metadataResourceProvider.getResourceDescription(INSTANCE)).thenReturn(Optional.of(resourceDescription()));
    var actual = getSearchFieldProvider().getPlainFieldByPath(INSTANCE, "id");
    assertThat(actual).isPresent().get().isEqualTo(plainField("keyword", List.of(SEARCH)));
  }

  @DisplayName("getPlainFieldByPath_parameterized")
  @ParameterizedTest(name = "[{index}] value=''{0}''")
  @CsvSource({",", "'',", "'   '", "path", "title.sub3"})
  void getPlainFieldByPath_negative_parameterized(String path) {
    when(metadataResourceProvider.getResourceDescription(INSTANCE)).thenReturn(Optional.of(resourceDescription()));
    var actual = getSearchFieldProvider().getPlainFieldByPath(INSTANCE, path);
    assertThat(actual).isEmpty();
  }

  @Test
  void getFieldByPath_negative_resourceDescriptionNotFound() {
    when(metadataResourceProvider.getResourceDescription(INSTANCE)).thenReturn(Optional.empty());
    var actual = getSearchFieldProvider().getPlainFieldByPath(INSTANCE, "id");
    assertThat(actual).isEmpty();
  }

  @ParameterizedTest
  @DisplayName("isSupportedLanguage_parameterized")
  @CsvSource({"eng,true", "ara,false", "spa,true"})
  void isSupportedLanguage_parameterized(String language, boolean expected) {
    var actual = getSearchFieldProvider().isSupportedLanguage(language);
    assertThat(actual).isEqualTo(expected);
  }

  @CsvSource({
    "cql.all,allInstance.*;plain_allInstance;allItems.*;plain_allItems;allHoldings.*;plain_allHoldings",
    "cql.allInstance,allInstance.*;plain_allInstance",
    "cql.allItems,allItems.*;plain_allItems",
    "cql.allHoldings,allHoldings.*;plain_allHoldings",
  })
  @ParameterizedTest
  @DisplayName("getFields_cqlAll_parameterized")
  void getFields_cqlAll_parameterized(String searchAlias, String fieldsAsString) {
    var expectedFields = List.of(fieldsAsString.split(";"));
    var actual = getSearchFieldProvider().getFields(INSTANCE, searchAlias);
    assertThat(actual).isEqualTo(expectedFields);
  }

  @ParameterizedTest
  @DisplayName("isMultilangField_parameterized")
  @CsvSource({"id,false", "allItems,true", "title1,true", "title2,false", "title2.sub1,false", "title2.sub2,true"})
  void isMultilangField_parameterized(String fieldName, boolean expected) {
    when(metadataResourceProvider.getResourceDescription(INSTANCE)).thenReturn(Optional.of(resourceDescription()));
    var actual = getSearchFieldProvider().isMultilangField(INSTANCE, fieldName);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void init_validateSearchAliases_failedToCreateAliasOnKeywordFacetField() {
    var plainField = plainField(List.of("alias"), FACET);
    var resourceDescription = resourceDescription(mapOf("field1", plainField, "field2", plainField), emptyMap());
    var searchFieldProvider = new LocalSearchFieldProvider(metadataResourceProvider, emptyMap());
    when(metadataResourceProvider.getResourceDescriptions()).thenReturn(List.of(resourceDescription));
    when(metadataResourceProvider.getSearchFieldTypes()).thenReturn(searchFieldTypes());

    assertThatThrownBy(searchFieldProvider::init)
      .isInstanceOf(ResourceDescriptionException.class)
      .hasMessage("Failed to create resource description for resource: 'instance', errors: "
                  + "[Invalid plain field descriptor for search alias 'alias'. Alias for field with searchType="
                  + "'facet' can't group more than 1 field.]");
  }

  @Test
  void init_validateSearchAliases_failedToCreateAliasesWithSearchTermProcessor() {
    var plainField = plainField(List.of("alias"));
    plainField.setSearchTermProcessor("testProcessor");
    var resourceDescription = resourceDescription(mapOf("field1", plainField, "field2", plainField), emptyMap());
    var searchFieldProvider = new LocalSearchFieldProvider(metadataResourceProvider, emptyMap());
    when(metadataResourceProvider.getResourceDescriptions()).thenReturn(List.of(resourceDescription));
    when(metadataResourceProvider.getSearchFieldTypes()).thenReturn(searchFieldTypes());

    assertThatThrownBy(searchFieldProvider::init)
      .isInstanceOf(ResourceDescriptionException.class)
      .hasMessage("Failed to create resource description for resource: 'instance', errors: "
                  + "[Invalid plain field descriptor for search alias 'alias'. Alias for field with "
                  + "searchTermProcessor can't group more than 1 field.]");
  }

  private LocalSearchFieldProvider getSearchFieldProvider() {
    var searchFieldProvider = new LocalSearchFieldProvider(metadataResourceProvider, emptyMap());
    when(metadataResourceProvider.getResourceDescriptions()).thenReturn(List.of(resourceDescription()));
    when(metadataResourceProvider.getSearchFieldTypes()).thenReturn(searchFieldTypes());
    searchFieldProvider.init();
    return searchFieldProvider;
  }

  private ResourceDescription resourceDescription() {
    return resourceDescription(mapOf(
        "id", plainField("keyword", List.of(SEARCH)),
        "allInstance", multilangField("cql.all", "cql.allInstance"),
        "allItems", multilangField("cql.all", "cql.allItems"),
        "allHoldings", multilangField("cql.all", "cql.allHoldings"),
        "contributors", objectField(mapOf("name", plainField("standard", List.of(SEARCH)))),
        "title1", plainField("multilang", List.of(SEARCH), TITLE_SEARCH_TYPE),
        "title2", objectField(mapOf(
          "sub1", plainField("keyword", List.of(SEARCH), TITLE_SEARCH_TYPE),
          "sub2", plainField("multilang", emptyList(), TITLE_SEARCH_TYPE),
          "sub3", objectField(mapOf(
            "sub4", plainField("keyword", emptyList(), TITLE_SEARCH_TYPE),
            "sub5", plainField("multilang", List.of(SEARCH)))))),
        "source", plainField("keyword", List.of(SEARCH)),
        "oldFieldName", plainField(List.of("newFieldName"), FACET, FILTER)
      ),
      mapOf(
        "search1", searchField(TITLE_SEARCH_TYPE),
        "search2", searchField())
    );
  }

  private ResourceDescription resourceDescription(
    Map<String, FieldDescription> fields, Map<String, SearchFieldDescriptor> searchFields) {

    var resourceDescription = TestUtils.resourceDescription(fields);
    resourceDescription.setSearchFields(searchFields);

    return converter.convert(resourceDescription);
  }

  private static Map<String, SearchFieldType> searchFieldTypes() {
    return mapOf("keyword", new SearchFieldType(), "multilang", SearchFieldType.of(multilangMappings()));
  }

  private static ObjectNode multilangMappings() {
    return jsonObject("properties", jsonObject(
      "eng", jsonObject("type", "text", "analyzer", "english"),
      "ger", jsonObject("type", "text", "analyzer", "german"),
      "spa", jsonObject("type", "text", "analyzer", "spanish"),
      "src", jsonObject("type", "text", "analyzer", "standard")
    ));
  }

  private static Stream<Arguments> getPlainFieldsByPathDataProvider() {
    return Stream.of(
      arguments("id", plainField("keyword", List.of(SEARCH))),
      arguments("title2.sub1", plainField("keyword", List.of(SEARCH), TITLE_SEARCH_TYPE)),
      arguments("title2.sub3.sub4", plainField("keyword", emptyList(), TITLE_SEARCH_TYPE)),
      arguments("title2.sub3", null),
      arguments("title4", null),
      arguments("title1.subfield", null),
      arguments("title2.subfield", null),
      arguments("title2.sub.sub.sub", null)
    );
  }

  private static PlainFieldDescription plainField(
    String index, List<ResponseGroupType> responseGroupTypes, String... searchAliases) {
    var fieldDescription = new PlainFieldDescription();
    fieldDescription.setIndex(index);
    fieldDescription.setSearchAliases(List.of(searchAliases));
    fieldDescription.setShowInResponse(responseGroupTypes);
    return fieldDescription;
  }

  private static PlainFieldDescription plainField(List<String> searchAliases, SearchType... searchTypes) {
    var fieldDescription = new PlainFieldDescription();
    fieldDescription.setIndex("keyword");
    fieldDescription.setSearchAliases(searchAliases);
    fieldDescription.setSearchTypes(List.of(searchTypes));
    return fieldDescription;
  }

  private static SearchFieldDescriptor searchField(String... searchAliases) {
    var fieldDescription = new SearchFieldDescriptor();
    fieldDescription.setIndex("keyword");
    fieldDescription.setSearchAliases(List.of(searchAliases));
    fieldDescription.setProcessor("processor");
    return fieldDescription;
  }

  private static SearchFieldModifier getModifier(String newValue) {
    var modifier = mock(SearchFieldModifier.class);
    when(modifier.modify(any())).thenReturn(newValue);
    return modifier;
  }
}
