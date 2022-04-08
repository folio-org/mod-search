package org.folio.search.service.metadata;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.model.types.SearchType.FACET;
import static org.folio.search.model.types.SearchType.FILTER;
import static org.folio.search.utils.JsonUtils.jsonObject;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.multilangField;
import static org.folio.search.utils.TestUtils.objectField;
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
import org.folio.search.model.types.SearchType;
import org.folio.search.utils.TestUtils;
import org.folio.search.utils.types.UnitTest;
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

  @Mock private MetadataResourceProvider metadataResourceProvider;
  private final PostProcessResourceDescriptionConverter converter = new PostProcessResourceDescriptionConverter();

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
    var fields = getSearchFieldProvider().getFields(RESOURCE_NAME, TITLE_SEARCH_TYPE);
    assertThat(fields).containsExactlyInAnyOrder(
      "title1.*", "title2.sub1", "title2.sub2.*", "title2.sub3.sub4", "search1");
  }

  @Test
  void getFieldByInventorySearchType_positive_nonExistingResource() {
    var fields = getSearchFieldProvider().getFields("some-resource", TITLE_SEARCH_TYPE);
    assertThat(fields).isEmpty();
  }

  @Test
  void getSourceFields_positive() {
    var actual = getSearchFieldProvider().getSourceFields(RESOURCE_NAME);
    assertThat(actual).containsExactlyInAnyOrder("id", "plain_title1", "title2.sub1",
      "title2.sub3.plain_sub5", "source");
  }

  @Test
  void getSourceFields_positive_nonExistingResource() {
    var actual = getSearchFieldProvider().getSourceFields("unknown-resource");
    assertThat(actual).isEmpty();
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
    when(metadataResourceProvider.getResourceDescription(RESOURCE_NAME)).thenReturn(Optional.of(resourceDescription));

    var actual = searchFieldProvider.getModifiedField(fieldName, RESOURCE_NAME);
    assertThat(actual).isEqualTo(modifiedField);
  }

  @Test
  void getModifiedField_positive_shouldNotModify() {
    var fieldName = "field";
    var searchFieldProvider = new LocalSearchFieldProvider(metadataResourceProvider, emptyMap());
    var actual = searchFieldProvider.getModifiedField(fieldName, RESOURCE_NAME);
    assertThat(actual).isEqualTo(fieldName);
  }

  @MethodSource("getPlainFieldsByPathDataProvider")
  @ParameterizedTest(name = "[{index}] path={0}")
  void getPlainFieldByPath_positive_parameterized(String path, FieldDescription expected) {
    when(metadataResourceProvider.getResourceDescription(RESOURCE_NAME)).thenReturn(Optional.of(resourceDescription()));
    var actual = getSearchFieldProvider().getPlainFieldByPath(RESOURCE_NAME, path);
    assertThat(actual).isEqualTo(Optional.ofNullable(expected));
  }

  @Test
  void getPlainFieldByPath_positive() {
    when(metadataResourceProvider.getResourceDescription(RESOURCE_NAME)).thenReturn(Optional.of(resourceDescription()));
    var actual = getSearchFieldProvider().getPlainFieldByPath(RESOURCE_NAME, "id");
    assertThat(actual).isPresent().get().isEqualTo(plainField("keyword", true));
  }

  @DisplayName("getPlainFieldByPath_parameterized")
  @ParameterizedTest(name = "[{index}] value=''{0}''")
  @CsvSource({",", "'',", "'   '", "path", "title.sub3"})
  void getPlainFieldByPath_negative_parameterized(String path) {
    when(metadataResourceProvider.getResourceDescription(RESOURCE_NAME)).thenReturn(Optional.of(resourceDescription()));
    var actual = getSearchFieldProvider().getPlainFieldByPath(RESOURCE_NAME, path);
    assertThat(actual).isEmpty();
  }

  @Test
  void getFieldByPath_negative_resourceDescriptionNotFound() {
    when(metadataResourceProvider.getResourceDescription(RESOURCE_NAME)).thenReturn(Optional.empty());
    var actual = getSearchFieldProvider().getPlainFieldByPath(RESOURCE_NAME, "id");
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
    var actual = getSearchFieldProvider().getFields(RESOURCE_NAME, searchAlias);
    assertThat(actual).isEqualTo(expectedFields);
  }

  @ParameterizedTest
  @DisplayName("isMultilangField_parameterized")
  @CsvSource({"id,false", "allItems,true", "title1,true", "title2,false", "title2.sub1,false", "title2.sub2,true"})
  void isMultilangField_parameterized(String fieldName, boolean expected) {
    when(metadataResourceProvider.getResourceDescription(RESOURCE_NAME)).thenReturn(Optional.of(resourceDescription()));
    var actual = getSearchFieldProvider().isMultilangField(RESOURCE_NAME, fieldName);
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
      .hasMessage("Failed to create resource description for resource: 'test-resource', errors: "
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
      .hasMessage("Failed to create resource description for resource: 'test-resource', errors: "
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
        "id", plainField("keyword", true),
        "allInstance", multilangField("cql.all", "cql.allInstance"),
        "allItems", multilangField("cql.all", "cql.allItems"),
        "allHoldings", multilangField("cql.all", "cql.allHoldings"),
        "title1", plainField("multilang", true, TITLE_SEARCH_TYPE),
        "title2", objectField(mapOf(
          "sub1", plainField("keyword", true, TITLE_SEARCH_TYPE),
          "sub2", plainField("multilang", false, TITLE_SEARCH_TYPE),
          "sub3", objectField(mapOf(
            "sub4", plainField("keyword", false, TITLE_SEARCH_TYPE),
            "sub5", plainField("multilang", true))))),
        "source", plainField("keyword", true),
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
      arguments("id", plainField("keyword", true)),
      arguments("title2.sub1", plainField("keyword", true, TITLE_SEARCH_TYPE)),
      arguments("title2.sub3.sub4", plainField("keyword", false, TITLE_SEARCH_TYPE)),
      arguments("title2.sub3", null),
      arguments("title4", null),
      arguments("title1.subfield", null),
      arguments("title2.subfield", null),
      arguments("title2.sub.sub.sub", null)
    );
  }

  private static PlainFieldDescription plainField(String index, boolean showInResponse, String... searchAliases) {
    var fieldDescription = new PlainFieldDescription();
    fieldDescription.setIndex(index);
    fieldDescription.setSearchAliases(List.of(searchAliases));
    fieldDescription.setShowInResponse(showInResponse);
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
