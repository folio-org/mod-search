package org.folio.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.reindex.IndexFamilyEntity;
import org.folio.search.model.types.IndexFamilyStatus;
import org.folio.search.model.types.QueryVersion;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.RestHighLevelClient;

@UnitTest
@ExtendWith(MockitoExtension.class)
class IndexFamilyServiceTest {

  @Mock
  private IndexFamilyRepository indexFamilyRepository;
  @Mock
  private RestHighLevelClient elasticsearchClient;
  @Mock
  private SearchSettingsHelper searchSettingsHelper;
  @Mock
  private SearchMappingsHelper searchMappingsHelper;
  @Mock
  private JsonConverter jsonConverter;
  @Mock
  private ReindexKafkaConsumerManager reindexKafkaConsumerManager;
  @Mock
  private StreamingReindexStatusRepository streamingReindexStatusRepository;
  @Mock
  private FolioExecutionContext context;

  @InjectMocks
  private IndexFamilyService service;

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
