package org.folio.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.opensearch.client.RequestOptions.DEFAULT;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.reindex.IndexFamilyEntity;
import org.folio.search.model.types.IndexFamilyStatus;
import org.folio.search.model.types.QueryVersion;
import org.folio.search.model.types.ResourceType;
import org.folio.search.repository.IndexNameProvider;
import org.folio.search.service.es.SearchMappingsHelper;
import org.folio.search.service.es.SearchSettingsHelper;
import org.folio.search.service.reindex.ReindexKafkaConsumerManager;
import org.folio.search.service.reindex.jdbc.IndexFamilyRepository;
import org.folio.search.service.reindex.jdbc.StreamingReindexStatusRepository;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.action.admin.indices.delete.DeleteIndexRequest;
import org.opensearch.client.IndicesClient;
import org.opensearch.client.RestHighLevelClient;

@UnitTest
@ExtendWith(MockitoExtension.class)
class IndexFamilyServiceTest {

  @Mock
  private IndexFamilyRepository indexFamilyRepository;
  @Mock
  private RestHighLevelClient elasticsearchClient;
  @Mock
  private IndicesClient indicesClient;
  @Mock
  private SearchSettingsHelper searchSettingsHelper;
  @Mock
  private SearchMappingsHelper searchMappingsHelper;
  @Mock
  private JsonConverter jsonConverter;
  @Mock
  private IndexNameProvider indexNameProvider;
  @Mock
  private ReindexKafkaConsumerManager reindexKafkaConsumerManager;
  @Mock
  private StreamingReindexStatusRepository streamingReindexStatusRepository;
  @Mock
  private FolioExecutionContext context;

  @InjectMocks
  private IndexFamilyService service;

  @Test
  void prepareLegacyV1RepresentativeFamily_createsBuildingRepresentativeWhenMissing() {
    var tenantId = "tenant";
    var indexName = "folio_instance_tenant";
    var entityCaptor = ArgumentCaptor.forClass(IndexFamilyEntity.class);

    when(indexNameProvider.getIndexName(ResourceType.INSTANCE, tenantId)).thenReturn(indexName);
    when(indexFamilyRepository.findByVersion(QueryVersion.V1)).thenReturn(java.util.List.of());
    when(indexFamilyRepository.getNextGeneration(QueryVersion.V1)).thenReturn(4);

    var family = service.prepareLegacyV1RepresentativeFamily(tenantId);

    verify(indexFamilyRepository).create(entityCaptor.capture());
    var created = entityCaptor.getValue();
    assertThat(family).isEqualTo(created);
    assertThat(created.getGeneration()).isEqualTo(4);
    assertThat(created.getIndexName()).isEqualTo(indexName);
    assertThat(created.getStatus()).isEqualTo(IndexFamilyStatus.BUILDING);
    assertThat(created.getActivatedAt()).isNull();
    assertThat(created.getQueryVersion()).isEqualTo(QueryVersion.V1);
  }

  @Test
  void prepareLegacyV1RepresentativeFamily_reusesLegacyRowWithoutDroppingIndex() {
    var tenantId = "tenant";
    var legacyIndexName = "folio_instance_tenant";
    var familyId = UUID.randomUUID();
    var family = new IndexFamilyEntity(familyId, 1, legacyIndexName, IndexFamilyStatus.RETIRING,
      Timestamp.from(Instant.now()), null, Timestamp.from(Instant.now()), QueryVersion.V1);
    var updatedFamily = new IndexFamilyEntity(familyId, 1, legacyIndexName, IndexFamilyStatus.BUILDING,
      family.getCreatedAt(), Timestamp.from(Instant.now()), null, QueryVersion.V1);

    when(indexNameProvider.getIndexName(ResourceType.INSTANCE, tenantId)).thenReturn(legacyIndexName);
    when(indexFamilyRepository.findByVersion(QueryVersion.V1)).thenReturn(java.util.List.of(family));
    when(indexFamilyRepository.findById(familyId)).thenReturn(Optional.of(updatedFamily));

    var result = service.prepareLegacyV1RepresentativeFamily(tenantId);

    assertThat(result).isEqualTo(updatedFamily);
    verify(reindexKafkaConsumerManager).stopReindexConsumer(familyId);
    verify(streamingReindexStatusRepository).deleteByFamilyId(familyId);
    verify(indexFamilyRepository).updateRepresentation(familyId, legacyIndexName, IndexFamilyStatus.BUILDING);
    verify(indexFamilyRepository, never()).deleteById(familyId);
    verifyNoMoreInteractions(indicesClient);
  }

  @Test
  void activateLegacyV1RepresentativeFamily_convertsFamilyManagedRowToLegacyIndex() throws Exception {
    var tenantId = "tenant";
    var legacyIndexName = "folio_instance_tenant";
    var familyId = UUID.randomUUID();
    var managedIndexName = "folio_instance_tenant_3";
    var family = new IndexFamilyEntity(familyId, 3, managedIndexName, IndexFamilyStatus.ACTIVE,
      Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), null, QueryVersion.V1);
    var updatedFamily = new IndexFamilyEntity(familyId, 3, legacyIndexName, IndexFamilyStatus.ACTIVE,
      family.getCreatedAt(), family.getActivatedAt(), null, QueryVersion.V1);
    var deleteCaptor = ArgumentCaptor.forClass(DeleteIndexRequest.class);

    when(indexNameProvider.getIndexName(ResourceType.INSTANCE, tenantId)).thenReturn(legacyIndexName);
    when(indexFamilyRepository.findByVersion(QueryVersion.V1)).thenReturn(java.util.List.of(family));
    when(indexFamilyRepository.findById(familyId)).thenReturn(Optional.of(updatedFamily));
    when(elasticsearchClient.indices()).thenReturn(indicesClient);
    when(indicesClient.exists(any(org.opensearch.client.indices.GetIndexRequest.class), eq(DEFAULT))).thenReturn(true);

    var result = service.activateLegacyV1RepresentativeFamily(tenantId);

    assertThat(result).contains(updatedFamily);
    verify(indicesClient).delete(deleteCaptor.capture(), eq(DEFAULT));
    assertThat(deleteCaptor.getValue().indices()).containsExactly(managedIndexName);
    verify(indexFamilyRepository).updateRepresentation(familyId, legacyIndexName, IndexFamilyStatus.ACTIVE);
  }

  @Test
  void activateLegacyV1RepresentativeFamily_skipsActivationWhenLegacyIndexIsMissing() throws Exception {
    var tenantId = "tenant";
    var legacyIndexName = "folio_instance_tenant";

    when(indexNameProvider.getIndexName(ResourceType.INSTANCE, tenantId)).thenReturn(legacyIndexName);
    when(elasticsearchClient.indices()).thenReturn(indicesClient);
    when(indicesClient.exists(any(org.opensearch.client.indices.GetIndexRequest.class), eq(DEFAULT))).thenReturn(false);

    var result = service.activateLegacyV1RepresentativeFamily(tenantId);

    assertThat(result).isEmpty();
    verify(indexFamilyRepository, never()).findByVersion(QueryVersion.V1);
  }

  @Test
  void switchOver_rejectsStagedFamilyWithoutTransitioningWhenLagging() {
    var familyId = UUID.randomUUID();
    var family = new IndexFamilyEntity(familyId, 2, "tenant_2", IndexFamilyStatus.STAGED,
      Timestamp.from(Instant.now()), null, null, QueryVersion.V2);

    when(indexFamilyRepository.findById(familyId)).thenReturn(Optional.of(family));
    when(reindexKafkaConsumerManager.getConsumerLagToTarget(familyId)).thenReturn(3L);

    var error = assertThrows(RequestValidationException.class, () -> service.switchOver(familyId));

    assertThat(error.getKey()).isEqualTo("consumerLag");
    assertThat(error.getValue()).isEqualTo("3");
    verify(indexFamilyRepository).lockByVersion(QueryVersion.V2);
    verify(reindexKafkaConsumerManager).captureTargetOffsets(familyId);
    verify(reindexKafkaConsumerManager).getConsumerLagToTarget(familyId);
    verify(indexFamilyRepository, never()).updateStatus(familyId, IndexFamilyStatus.CUTTING_OVER);
    verify(reindexKafkaConsumerManager, never()).stopReindexConsumer(familyId);
  }

  @Test
  void switchOver_transitionsStagedFamilyWhenLagIsZero() {
    var familyId = UUID.randomUUID();
    var family = new IndexFamilyEntity(familyId, 2, "tenant_2", IndexFamilyStatus.STAGED,
      Timestamp.from(Instant.now()), null, null, QueryVersion.V2);

    when(context.getTenantId()).thenReturn("tenant");
    when(indexFamilyRepository.findById(familyId)).thenReturn(Optional.of(family));
    when(indexFamilyRepository.findActiveByVersion(QueryVersion.V2)).thenReturn(Optional.empty());
    when(reindexKafkaConsumerManager.getConsumerLagToTarget(familyId)).thenReturn(0L);

    assertThrows(Exception.class, () -> service.switchOver(familyId));

    verify(reindexKafkaConsumerManager).captureTargetOffsets(familyId);
    verify(reindexKafkaConsumerManager).getConsumerLagToTarget(familyId);
    verify(indexFamilyRepository).updateStatus(familyId, IndexFamilyStatus.CUTTING_OVER);
  }

  @Test
  void switchOver_proceedsFromCuttingOverWhenLagIsZero() {
    var familyId = UUID.randomUUID();
    var family = new IndexFamilyEntity(familyId, 2, "tenant_2", IndexFamilyStatus.CUTTING_OVER,
      Timestamp.from(Instant.now()), null, null, QueryVersion.V2);

    when(context.getTenantId()).thenReturn("tenant");
    when(indexFamilyRepository.findById(familyId)).thenReturn(Optional.of(family));
    when(indexFamilyRepository.findActiveByVersion(QueryVersion.V2)).thenReturn(Optional.empty());
    when(reindexKafkaConsumerManager.getConsumerLagToTarget(familyId)).thenReturn(0L);

    assertThrows(Exception.class, () -> service.switchOver(familyId));

    verify(indexFamilyRepository).lockByVersion(QueryVersion.V2);
    verify(reindexKafkaConsumerManager).captureTargetOffsets(familyId);
    verify(reindexKafkaConsumerManager).getConsumerLagToTarget(familyId);
    verify(indexFamilyRepository, never()).updateStatus(familyId, IndexFamilyStatus.CUTTING_OVER);
  }
}
