package org.folio.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.utils.SearchResponseHelper.getSuccessFolioCreateIndexResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.search.utils.SearchUtils.AUTHORITY_RESOURCE;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.SearchUtils.INSTANCE_SUBJECT_RESOURCE;
import static org.folio.search.utils.SearchUtils.getIndexName;
import static org.folio.search.utils.SearchUtils.getResourceName;
import static org.folio.search.utils.TestConstants.EMPTY_JSON_OBJECT;
import static org.folio.search.utils.TestConstants.EMPTY_OBJECT;
import static org.folio.search.utils.TestConstants.INDEX_NAME;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.resourceDescription;
import static org.folio.search.utils.TestUtils.secondaryResourceDescription;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.folio.search.client.ResourceReindexClient;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.IndexSettings;
import org.folio.search.domain.dto.ReindexJob;
import org.folio.search.domain.dto.ReindexRequest;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.repository.IndexRepository;
import org.folio.search.service.es.SearchMappingsHelper;
import org.folio.search.service.es.SearchSettingsHelper;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.folio.spring.test.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class IndexServiceTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @InjectMocks
  private IndexService indexService;

  @Mock
  private IndexRepository indexRepository;
  @Mock
  private SearchMappingsHelper mappingsHelper;
  @Mock
  private SearchSettingsHelper settingsHelper;
  @Mock
  private ResourceReindexClient resourceReindexClient;
  @Mock
  private ResourceDescriptionService resourceDescriptionService;

  @Test
  void createIndex_positive() {
    var expectedResponse = getSuccessFolioCreateIndexResponse(List.of(INDEX_NAME));

    when(resourceDescriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription(RESOURCE_NAME));
    when(mappingsHelper.getMappings(RESOURCE_NAME)).thenReturn(EMPTY_OBJECT);
    when(settingsHelper.getSettings(RESOURCE_NAME)).thenReturn(EMPTY_OBJECT);
    when(indexRepository.createIndex(INDEX_NAME, EMPTY_OBJECT, EMPTY_OBJECT)).thenReturn(expectedResponse);

    var indexResponse = indexService.createIndex(RESOURCE_NAME, TENANT_ID);
    assertThat(indexResponse).isEqualTo(expectedResponse);
  }

  @ParameterizedTest
  @MethodSource("customSettingsTestData")
  @SneakyThrows
  void createIndex_positive_customSettings(Integer shards, Integer replicas, Integer refresh) {
    var expectedResponse = getSuccessFolioCreateIndexResponse(List.of(INDEX_NAME));

    var indexSettingsMock = MAPPER.readTree(getIndexSettingsJsonString(4, 2, "1s"));
    var indexSettingsRequest = new IndexSettings()
      .numberOfShards(shards)
      .numberOfReplicas(replicas)
      .refreshInterval(refresh);

    var expectedShards = shards == null ? 4 : shards;
    var expectedReplicas = replicas == null ? 2 : replicas;
    var expectedRefresh = refresh == null || refresh == 0 ? "1s"
      : refresh < 0 ? String.valueOf(refresh) : refresh + "s";
    var expectedIndexSettings = getIndexSettingsJsonString(expectedShards, expectedReplicas, expectedRefresh);

    when(resourceDescriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription(RESOURCE_NAME));
    when(mappingsHelper.getMappings(RESOURCE_NAME)).thenReturn(EMPTY_OBJECT);
    when(settingsHelper.getSettingsJson(RESOURCE_NAME)).thenReturn(indexSettingsMock);
    when(indexRepository.createIndex(INDEX_NAME, expectedIndexSettings, EMPTY_OBJECT)).thenReturn(expectedResponse);

    var indexResponse = indexService.createIndex(RESOURCE_NAME, TENANT_ID, indexSettingsRequest);
    assertThat(indexResponse).isEqualTo(expectedResponse);
  }

  private static Stream<Arguments> customSettingsTestData() {
    return Stream.of(
      Arguments.of(1, 1, 2),
      Arguments.of(null, null, null),
      Arguments.of(null, 1, null),
      Arguments.of(null, null, -1),
      Arguments.of(null, null, 0)
    );
  }

  @Test
  @SneakyThrows
  void createIndex_positive_customSettingsNull() {
    var expectedResponse = getSuccessFolioCreateIndexResponse(List.of(INDEX_NAME));

    var expectedIndexSettings = getIndexSettingsJsonString(4, 2, "1s");
    var indexSettingsMock = MAPPER.readTree(expectedIndexSettings);

    when(resourceDescriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription(RESOURCE_NAME));
    when(mappingsHelper.getMappings(RESOURCE_NAME)).thenReturn(EMPTY_OBJECT);
    when(settingsHelper.getSettingsJson(RESOURCE_NAME)).thenReturn(indexSettingsMock);
    when(indexRepository.createIndex(INDEX_NAME, expectedIndexSettings, EMPTY_OBJECT)).thenReturn(expectedResponse);

    var indexResponse = indexService.createIndex(RESOURCE_NAME, TENANT_ID, null);
    assertThat(indexResponse).isEqualTo(expectedResponse);
  }

  @SneakyThrows
  private String getIndexSettingsJsonString(Integer shards, Integer replicas, String refresh) {
    return MAPPER.writeValueAsString(Map.of("index", Map.of(
      "number_of_shards", shards,
      "number_of_replicas", replicas,
      "refresh_interval", refresh)));
  }

  @Test
  void createIndex_negative_resourceDescriptionNotFound() {
    when(resourceDescriptionService.get(RESOURCE_NAME)).thenReturn(null);
    assertThatThrownBy(() -> indexService.createIndex(RESOURCE_NAME, TENANT_ID))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Index cannot be created for the resource because resource description is not found.");
  }

  @Test
  void updateMappings() {
    var expectedResponse = getSuccessIndexOperationResponse();

    when(resourceDescriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription(RESOURCE_NAME));
    when(mappingsHelper.getMappings(RESOURCE_NAME)).thenReturn(EMPTY_OBJECT);
    when(indexRepository.updateMappings(INDEX_NAME, EMPTY_OBJECT)).thenReturn(expectedResponse);

    var indexResponse = indexService.updateMappings(RESOURCE_NAME, TENANT_ID);
    assertThat(indexResponse).isEqualTo(expectedResponse);
  }

  @Test
  void updateMappings_negative_resourceDescriptionNotFound() {
    when(resourceDescriptionService.get(RESOURCE_NAME)).thenReturn(null);
    assertThatThrownBy(() -> indexService.updateMappings(RESOURCE_NAME, TENANT_ID))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Mappings cannot be updated, resource name is invalid.");
  }

  @Test
  void createIndexIfNotExist_shouldCreateIndex_indexNotExist() {
    when(resourceDescriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription(RESOURCE_NAME));
    var indexName = getIndexName(RESOURCE_NAME, TENANT_ID);

    indexService.createIndexIfNotExist(RESOURCE_NAME, TENANT_ID);

    verify(indexRepository).createIndex(eq(indexName), any(), any());
  }

  @Test
  void createIndexIfNotExist_shouldNotCreateIndex_alreadyExist() {
    var indexName = getIndexName(RESOURCE_NAME, TENANT_ID);
    when(indexRepository.indexExists(indexName)).thenReturn(true);

    indexService.createIndexIfNotExist(RESOURCE_NAME, TENANT_ID);

    verify(indexRepository, times(0)).createIndex(eq(indexName), any(), any());
  }

  @Test
  void reindexInventory_positive_recreateIndexIsTrue() {
    var indexName = getIndexName(INSTANCE_RESOURCE, TENANT_ID);
    var createIndexResponse = getSuccessFolioCreateIndexResponse(List.of(indexName));
    var expectedResponse = new ReindexJob().id(randomId());
    var expectedUri = URI.create("http://instance-storage/reindex");

    when(resourceReindexClient.submitReindex(expectedUri)).thenReturn(expectedResponse);
    when(mappingsHelper.getMappings(INSTANCE_RESOURCE)).thenReturn(EMPTY_OBJECT);
    when(settingsHelper.getSettingsJson(INSTANCE_RESOURCE)).thenReturn(EMPTY_JSON_OBJECT);
    when(indexRepository.indexExists(indexName)).thenReturn(true, false);
    when(indexRepository.createIndex(indexName, EMPTY_OBJECT, EMPTY_OBJECT)).thenReturn(createIndexResponse);
    when(resourceDescriptionService.get(INSTANCE_RESOURCE)).thenReturn(resourceDescription(INSTANCE_RESOURCE));

    var actual = indexService.reindexInventory(TENANT_ID, new ReindexRequest().recreateIndex(true));

    assertThat(actual).isEqualTo(expectedResponse);
    verify(indexRepository).dropIndex(indexName);
  }

  @Test
  void reindexInventory_positive_recreateIndexIsFalse() {
    var expectedResponse = new ReindexJob().id(randomId());
    var expectedUri = URI.create("http://instance-storage/reindex");

    when(resourceReindexClient.submitReindex(expectedUri)).thenReturn(expectedResponse);
    when(resourceDescriptionService.get(INSTANCE_RESOURCE)).thenReturn(resourceDescription(INSTANCE_RESOURCE));

    var actual = indexService.reindexInventory(TENANT_ID, new ReindexRequest());
    assertThat(actual).isEqualTo(expectedResponse);
  }

  @Test
  void reindexInventory_positive_resourceNameIsNull() {
    var expectedResponse = new ReindexJob().id(randomId());
    var expectedUri = URI.create("http://instance-storage/reindex");

    when(resourceDescriptionService.get(INSTANCE_RESOURCE)).thenReturn(resourceDescription(INSTANCE_RESOURCE));
    when(resourceDescriptionService.getSecondaryResourceNames(INSTANCE_RESOURCE)).thenReturn(List.of("secondary"));
    when(resourceReindexClient.submitReindex(expectedUri)).thenReturn(expectedResponse);

    var actual = indexService.reindexInventory(TENANT_ID, new ReindexRequest().resourceName(null));
    assertThat(actual).isEqualTo(expectedResponse);
  }

  @Test
  void reindexInventory_positive_resourceNameIsNullAndRecreateIndexIsTrue() {
    var expectedResponse = new ReindexJob().id(randomId());
    var expectedUri = URI.create("http://instance-storage/reindex");

    when(resourceDescriptionService.get(INSTANCE_RESOURCE)).thenReturn(resourceDescription(INSTANCE_RESOURCE));
    when(resourceDescriptionService.get(INSTANCE_SUBJECT_RESOURCE))
      .thenReturn(secondaryResourceDescription(INSTANCE_SUBJECT_RESOURCE, INSTANCE_RESOURCE));
    when(resourceReindexClient.submitReindex(expectedUri)).thenReturn(expectedResponse);
    when(resourceDescriptionService.getSecondaryResourceNames(INSTANCE_RESOURCE))
      .thenReturn(List.of(INSTANCE_SUBJECT_RESOURCE));
    mockCreateIndexOperation(INSTANCE_RESOURCE);
    mockCreateIndexOperation(INSTANCE_SUBJECT_RESOURCE);

    var secondaryIndexName = getIndexName(INSTANCE_SUBJECT_RESOURCE, TENANT_ID);
    var instanceIndexName = getIndexName(INSTANCE_RESOURCE, TENANT_ID);
    when(indexRepository.indexExists(instanceIndexName)).thenReturn(true);
    when(indexRepository.indexExists(secondaryIndexName)).thenReturn(true);

    var actual = indexService.reindexInventory(TENANT_ID, new ReindexRequest().resourceName(null).recreateIndex(true));
    assertThat(actual).isEqualTo(expectedResponse);

    verify(indexRepository).dropIndex(instanceIndexName);
    verify(indexRepository).dropIndex(secondaryIndexName);
  }

  private void mockCreateIndexOperation(String resource) {
    var indexName = getIndexName(resource, TENANT_ID);
    doReturn(EMPTY_OBJECT).when(mappingsHelper).getMappings(resource);
    doReturn(EMPTY_JSON_OBJECT).when(settingsHelper).getSettingsJson(resource);
    doReturn(getSuccessFolioCreateIndexResponse(List.of(indexName))).when(indexRepository)
      .createIndex(indexName, EMPTY_OBJECT, EMPTY_OBJECT);
  }

  @Test
  void reindexInventory_positive_reindexRequestIsNull() {
    var expectedResponse = new ReindexJob().id(randomId());
    var expectedUri = URI.create("http://instance-storage/reindex");

    when(resourceReindexClient.submitReindex(expectedUri)).thenReturn(expectedResponse);
    when(resourceDescriptionService.get(INSTANCE_RESOURCE)).thenReturn(resourceDescription(INSTANCE_RESOURCE));
    when(resourceDescriptionService.getSecondaryResourceNames(INSTANCE_RESOURCE)).thenReturn(List.of("secondary"));

    var actual = indexService.reindexInventory(TENANT_ID, null);

    assertThat(actual).isEqualTo(expectedResponse);
  }

  @Test
  void reindexInventory_positive_authorityRecord() {
    var expectedResponse = new ReindexJob().id(randomId());
    var expectedUri = URI.create("http://authority-storage/reindex");
    var resourceName = getResourceName(Authority.class);

    when(resourceReindexClient.submitReindex(expectedUri)).thenReturn(expectedResponse);
    when(resourceDescriptionService.get(resourceName)).thenReturn(resourceDescription(resourceName));

    var actual = indexService.reindexInventory(TENANT_ID, new ReindexRequest().resourceName(resourceName));

    assertThat(actual).isEqualTo(expectedResponse);
  }

  @Test
  void reindexInventory_positive_authorityRecordAndRecreateIndex() {
    var reindexResponse = new ReindexJob().id(randomId());
    var expectedUri = URI.create("http://authority-storage/reindex");
    var indexName = getIndexName(AUTHORITY_RESOURCE, TENANT_ID);

    when(resourceReindexClient.submitReindex(expectedUri)).thenReturn(reindexResponse);
    when(resourceDescriptionService.get(AUTHORITY_RESOURCE)).thenReturn(resourceDescription(AUTHORITY_RESOURCE));
    when(mappingsHelper.getMappings(AUTHORITY_RESOURCE)).thenReturn(EMPTY_OBJECT);
    when(settingsHelper.getSettingsJson(AUTHORITY_RESOURCE)).thenReturn(EMPTY_JSON_OBJECT);
    when(indexRepository.createIndex(indexName, EMPTY_OBJECT, EMPTY_OBJECT))
      .thenReturn(getSuccessFolioCreateIndexResponse(List.of(indexName)));

    var reindexRequest = new ReindexRequest().resourceName(AUTHORITY_RESOURCE).recreateIndex(true);
    var actual = indexService.reindexInventory(TENANT_ID, reindexRequest);

    assertThat(actual).isEqualTo(reindexResponse);
  }

  @Test
  void reindexInventory_negative_unknownResourceName() {
    var request = new ReindexRequest().resourceName("unknown");
    when(resourceDescriptionService.get("unknown")).thenReturn(null);
    assertThatThrownBy(() -> indexService.reindexInventory(TENANT_ID, request))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Reindex request contains invalid resource name");
  }

  @Test
  void reindexInventory_negative_secondaryResource() {
    var resource = "instance_subjects";
    var request = new ReindexRequest().resourceName(resource);
    when(resourceDescriptionService.get(resource)).thenReturn(secondaryResourceDescription(resource, RESOURCE_NAME));
    assertThatThrownBy(() -> indexService.reindexInventory(TENANT_ID, request))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Reindex request contains invalid resource name");
  }

  @Test
  void shouldDropIndexWhenExists() {
    when(indexRepository.indexExists(INDEX_NAME)).thenReturn(true);
    indexService.dropIndex(RESOURCE_NAME, TENANT_ID);
    verify(indexRepository).dropIndex(INDEX_NAME);
  }

  @Test
  void shouldNotDropIndexWhenNotExist() {
    when(indexRepository.indexExists(INDEX_NAME)).thenReturn(false);
    indexService.dropIndex(RESOURCE_NAME, TENANT_ID);
    verify(indexRepository, times(0)).dropIndex(INDEX_NAME);
  }
}
