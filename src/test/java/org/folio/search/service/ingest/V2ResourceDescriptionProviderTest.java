package org.folio.search.service.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.model.types.ResourceType.INSTANCE;
import static org.folio.search.utils.TestUtils.keywordField;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.objectField;
import static org.folio.search.utils.TestUtils.plainField;
import static org.folio.search.utils.TestUtils.searchField;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.folio.search.domain.dto.Instance;
import org.folio.search.model.metadata.FieldDescription;
import org.folio.search.model.metadata.ResourceDescription;
import org.folio.search.model.metadata.SearchFieldDescriptor;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class V2ResourceDescriptionProviderTest {

  @Mock
  private ResourceDescriptionService resourceDescriptionService;

  private V2ResourceDescriptionProvider provider;

  @BeforeEach
  void setUp() {
    when(resourceDescriptionService.get(INSTANCE)).thenReturn(sourceDescription());
    provider = new V2ResourceDescriptionProvider(resourceDescriptionService);
    provider.init();
  }

  @Test
  void getV2InstanceDescription_shouldExcludeItemsAndHoldingsFromFields() {
    var description = provider.getV2InstanceDescription();

    assertThat(description.getFields()).doesNotContainKeys("items", "holdings");
  }

  @Test
  void getV2InstanceDescription_shouldExcludeNestedDataSearchFields() {
    var description = provider.getV2InstanceDescription();

    assertThat(description.getSearchFields()).doesNotContainKeys(
      "itemPublicNotes",
      "holdingsPublicNotes",
      "itemTags",
      "holdingsTags",
      "holdingsTypeId",
      "statisticalCodes",
      "itemFullCallNumbers",
      "holdingsFullCallNumbers",
      "itemNormalizedCallNumbers",
      "holdingsNormalizedCallNumbers",
      "holdingsIdentifiers",
      "itemIdentifiers",
      "allItems",
      "allHoldings"
    );
  }

  @Test
  void getV2InstanceDescription_shouldPreserveInstanceLevelFieldsAndSearchFields() {
    var description = provider.getV2InstanceDescription();

    assertThat(description.getFields()).containsKeys("id", "title", "contributors");
    assertThat(description.getSearchFields()).containsKeys("sort_title", "isbn", "publicNotes");
  }

  @Test
  void getV2InstanceDescription_shouldNotAffectOriginalDescription() {
    var description = provider.getV2InstanceDescription();

    // Mutate the returned description's maps
    description.getFields().put("extraField", plainField("keyword"));
    description.getSearchFields().put("extraSearchField", searchField("proc"));

    // Original should be unaffected
    var original = resourceDescriptionService.get(INSTANCE);
    assertThat(original.getFields()).doesNotContainKey("extraField");
    assertThat(original.getSearchFields()).doesNotContainKey("extraSearchField");

    // Original should still contain items/holdings
    assertThat(original.getFields()).containsKeys("items", "holdings");
  }

  @Test
  void getV2InstanceDescription_shouldPreserveEventBodyJavaClass() {
    var description = provider.getV2InstanceDescription();

    assertThat(description.getEventBodyJavaClass()).isEqualTo(Instance.class);
  }

  @Test
  void getV2InstanceDescription_shouldPreserveResourceName() {
    var description = provider.getV2InstanceDescription();

    assertThat(description.getName()).isEqualTo(INSTANCE);
  }

  // -- helpers --

  private static ResourceDescription sourceDescription() {
    var description = new ResourceDescription();
    description.setName(INSTANCE);
    description.setEventBodyJavaClass(Instance.class);
    description.setReindexSupported(true);
    description.setLanguageSourcePaths(List.of("$.languages"));
    description.setFields(sourceFields());
    description.setSearchFields(sourceSearchFields());
    return description;
  }

  private static Map<String, FieldDescription> sourceFields() {
    return new LinkedHashMap<>(mapOf(
      "id", plainField("keyword"),
      "title", plainField("multilang"),
      "contributors", objectField(mapOf("name", keywordField())),
      "items", objectField(mapOf("id", keywordField(), "barcode", keywordField())),
      "holdings", objectField(mapOf("id", keywordField(), "hrid", keywordField()))
    ));
  }

  private static Map<String, SearchFieldDescriptor> sourceSearchFields() {
    return new LinkedHashMap<>(mapOf(
      "sort_title", searchField("sortTitleProcessor"),
      "isbn", searchField("isbnProcessor"),
      "publicNotes", searchField("publicNotesProcessor"),
      "itemPublicNotes", searchField("itemPublicNotesProcessor"),
      "holdingsPublicNotes", searchField("holdingsPublicNotesProcessor"),
      "itemTags", searchField("itemTagsProcessor"),
      "holdingsTags", searchField("holdingsTagsProcessor"),
      "holdingsTypeId", searchField("holdingsTypeIdProcessor"),
      "statisticalCodes", searchField("statisticalCodesProcessor"),
      "itemFullCallNumbers", searchField("effectiveCallNumberComponentsProcessor"),
      "holdingsFullCallNumbers", searchField("holdingsCallNumberComponentsProcessor"),
      "itemNormalizedCallNumbers", searchField("itemNormalizedCallNumbersProcessor"),
      "holdingsNormalizedCallNumbers", searchField("holdingsNormalizedCallNumbersProcessor"),
      "holdingsIdentifiers", searchField("holdingsIdentifiersProcessor"),
      "itemIdentifiers", searchField("itemIdentifiersProcessor"),
      "allItems", searchField("itemAllFieldValuesProcessor"),
      "allHoldings", searchField("holdingAllFieldValuesProcessor")
    ));
  }
}
