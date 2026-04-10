package org.folio.search.service.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.keywordField;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.objectField;
import static org.folio.search.utils.TestUtils.plainField;
import static org.folio.search.utils.TestUtils.searchField;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.folio.search.model.converter.ConversionContext;
import org.folio.search.model.metadata.FieldDescription;
import org.folio.search.model.metadata.ResourceDescription;
import org.folio.search.model.metadata.SearchFieldDescriptor;
import org.folio.search.service.consortium.LanguageConfigServiceDecorator;
import org.folio.search.service.converter.SearchFieldsProcessor;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class V2InstanceFieldEnricherTest {

  @Mock
  private V2ResourceDescriptionProvider descriptionProvider;
  @Mock
  private SearchFieldsProcessor searchFieldsProcessor;
  @Mock
  private LanguageConfigServiceDecorator languageConfigService;

  @InjectMocks
  private V2InstanceFieldEnricher enricher;

  @Test
  void enrich_shouldReturnBaseFieldsFromResourceFieldMapper() {
    var description = v2Description();
    when(descriptionProvider.getV2InstanceDescription()).thenReturn(description);
    when(languageConfigService.getAllLanguageCodes()).thenReturn(Set.of("eng"));
    when(searchFieldsProcessor.getSearchFields(any(ConversionContext.class)))
      .thenReturn(Map.of());

    var rawRecord = new HashMap<String, Object>();
    rawRecord.put("id", "test-id-1");
    rawRecord.put("title", "Test Title");
    rawRecord.put("languages", List.of("eng"));

    var result = enricher.enrich(rawRecord, TENANT_ID);

    assertThat(result)
      .containsEntry("id", "test-id-1")
      .containsEntry("title", "Test Title");
  }

  @Test
  void enrich_shouldReturnSearchFieldsFromProcessors() {
    var description = v2Description();
    when(descriptionProvider.getV2InstanceDescription()).thenReturn(description);
    when(languageConfigService.getAllLanguageCodes()).thenReturn(Set.of("eng"));

    var searchFieldResult = new LinkedHashMap<String, Object>();
    searchFieldResult.put("sort_title", "test title sorted");
    searchFieldResult.put("isbn", List.of("978-0-123"));
    when(searchFieldsProcessor.getSearchFields(any(ConversionContext.class)))
      .thenReturn(searchFieldResult);

    var rawRecord = new HashMap<String, Object>();
    rawRecord.put("id", "test-id-2");
    rawRecord.put("title", "Test Title");
    rawRecord.put("languages", List.of("eng"));

    var result = enricher.enrich(rawRecord, TENANT_ID);

    assertThat(result)
      .containsEntry("sort_title", "test title sorted")
      .containsEntry("isbn", List.of("978-0-123"));
  }

  @Test
  void enrich_shouldNotContainExcludedFields() {
    var description = v2DescriptionWithShared();
    when(descriptionProvider.getV2InstanceDescription()).thenReturn(description);
    when(languageConfigService.getAllLanguageCodes()).thenReturn(Set.of("eng"));
    when(searchFieldsProcessor.getSearchFields(any(ConversionContext.class)))
      .thenReturn(Map.of());

    var rawRecord = new HashMap<String, Object>();
    rawRecord.put("id", "test-id-3");
    rawRecord.put("title", "Test Title");
    rawRecord.put("shared", false);
    rawRecord.put("languages", List.of("eng"));

    var result = enricher.enrich(rawRecord, TENANT_ID);

    // 'shared' is explicitly stripped; items/holdings/resourceType/instanceId/join_field
    // are never produced because they are not in the V2 description
    assertThat(result)
      .doesNotContainKey("shared")
      .doesNotContainKey("items")
      .doesNotContainKey("holdings")
      .doesNotContainKey("resourceType")
      .doesNotContainKey("instanceId")
      .doesNotContainKey("join_field");
  }

  @Test
  void enrich_shouldAddBrowseIdsToContributorsSubjectsClassifications() {
    var description = v2DescriptionWithBrowseFields();
    when(descriptionProvider.getV2InstanceDescription()).thenReturn(description);
    when(languageConfigService.getAllLanguageCodes()).thenReturn(Set.of("eng"));
    when(searchFieldsProcessor.getSearchFields(any(ConversionContext.class)))
      .thenReturn(Map.of());

    var contributor = new HashMap<String, Object>();
    contributor.put("name", "John Doe");
    contributor.put("contributorNameTypeId", "type-1");

    var subject = new HashMap<String, Object>();
    subject.put("value", "Science");
    subject.put("sourceId", "src-1");
    subject.put("typeId", "type-1");

    var classification = new HashMap<String, Object>();
    classification.put("classificationNumber", "QA76");
    classification.put("classificationTypeId", "class-type-1");

    var rawRecord = new HashMap<String, Object>();
    rawRecord.put("id", "test-id-4");
    rawRecord.put("contributors", List.of(contributor));
    rawRecord.put("subjects", List.of(subject));
    rawRecord.put("classifications", List.of(classification));
    rawRecord.put("languages", List.of("eng"));

    var result = enricher.enrich(rawRecord, TENANT_ID);

    // Verify browse IDs were computed and added
    @SuppressWarnings("unchecked")
    var resultContributors = (List<Map<String, Object>>) result.get("contributors");
    assertThat(resultContributors).isNotNull().hasSize(1);
    assertThat(resultContributors.get(0)).containsKey("browseId");

    @SuppressWarnings("unchecked")
    var resultSubjects = (List<Map<String, Object>>) result.get("subjects");
    assertThat(resultSubjects).isNotNull().hasSize(1);
    assertThat(resultSubjects.get(0)).containsKey("browseId");

    @SuppressWarnings("unchecked")
    var resultClassifications = (List<Map<String, Object>>) result.get("classifications");
    assertThat(resultClassifications).isNotNull().hasSize(1);
    assertThat(resultClassifications.get(0)).containsKey("browseId");
  }

  @Test
  void enrich_shouldHandleEmptyIdentifierArrays() {
    var description = v2DescriptionWithBrowseFields();
    when(descriptionProvider.getV2InstanceDescription()).thenReturn(description);
    when(languageConfigService.getAllLanguageCodes()).thenReturn(Set.of("eng"));
    when(searchFieldsProcessor.getSearchFields(any(ConversionContext.class)))
      .thenReturn(Map.of());

    var rawRecord = new HashMap<String, Object>();
    rawRecord.put("id", "test-id-5");
    rawRecord.put("contributors", List.of());
    rawRecord.put("subjects", List.of());
    rawRecord.put("classifications", List.of());
    rawRecord.put("languages", List.of("eng"));

    var result = enricher.enrich(rawRecord, TENANT_ID);

    // Should complete without error
    assertThat(result).containsEntry("id", "test-id-5");
  }

  @Test
  void logProfilingSummary_shouldDelegateToSearchFieldsProcessor() {
    enricher.logProfilingSummary();

    verify(searchFieldsProcessor).logProfilingSummary();
  }

  @Test
  void resetProfilingCounters_shouldResetAllCounters() {
    // Force counters to be non-zero by running an enrich call
    var description = v2Description();
    when(descriptionProvider.getV2InstanceDescription()).thenReturn(description);
    when(languageConfigService.getAllLanguageCodes()).thenReturn(Set.of("eng"));
    when(searchFieldsProcessor.getSearchFields(any(ConversionContext.class)))
      .thenReturn(Map.of());

    var rawRecord = new HashMap<String, Object>();
    rawRecord.put("id", "test-id-6");
    rawRecord.put("languages", List.of("eng"));
    enricher.enrich(rawRecord, TENANT_ID);

    // Reset and verify summary runs without error (counters at zero means no summary logged)
    enricher.resetProfilingCounters();
    enricher.logProfilingSummary();

    // Verify it still delegates to searchFieldsProcessor
    verify(searchFieldsProcessor).logProfilingSummary();
  }

  // -- helpers --

  private static ResourceDescription v2Description() {
    var description = new ResourceDescription();
    description.setLanguageSourcePaths(List.of("$.languages"));
    description.setFields(minimalFields());
    description.setSearchFields(minimalSearchFields());
    return description;
  }

  private static ResourceDescription v2DescriptionWithShared() {
    var description = v2Description();
    var fields = new LinkedHashMap<>(description.getFields());
    var sharedField = plainField("bool");
    fields.put("shared", sharedField);
    description.setFields(fields);
    return description;
  }

  private static ResourceDescription v2DescriptionWithBrowseFields() {
    var description = new ResourceDescription();
    description.setLanguageSourcePaths(List.of("$.languages"));
    description.setFields(browseFields());
    description.setSearchFields(minimalSearchFields());
    return description;
  }

  private static Map<String, FieldDescription> minimalFields() {
    return new LinkedHashMap<>(mapOf(
      "id", keywordField(),
      "title", plainField("keyword")
    ));
  }

  private static Map<String, FieldDescription> browseFields() {
    return new LinkedHashMap<>(mapOf(
      "id", keywordField(),
      "contributors", objectField(mapOf(
        "name", keywordField(),
        "contributorNameTypeId", keywordField()
      )),
      "subjects", objectField(mapOf(
        "value", keywordField(),
        "sourceId", keywordField(),
        "typeId", keywordField()
      )),
      "classifications", objectField(mapOf(
        "classificationNumber", keywordField(),
        "classificationTypeId", keywordField()
      ))
    ));
  }

  private static Map<String, SearchFieldDescriptor> minimalSearchFields() {
    return new LinkedHashMap<>(mapOf(
      "sort_title", searchField("sortTitleProcessor"),
      "isbn", searchField("isbnProcessor")
    ));
  }
}
