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

  @InjectMocks
  private IndexFamilyService service;

  @Test
  void switchOver_usesLagToCapturedTarget() {
    var familyId = UUID.randomUUID();
    var family = new IndexFamilyEntity(familyId, "tenant", 2, "tenant_2", IndexFamilyStatus.BUILDING,
      Timestamp.from(Instant.now()), null, null, QueryVersion.V2);

    when(indexFamilyRepository.findById(familyId)).thenReturn(Optional.of(family));
    when(indexFamilyRepository.findActiveByTenantIdAndVersion("tenant", QueryVersion.V2))
      .thenReturn(Optional.empty());
    when(reindexKafkaConsumerManager.getConsumerLagToTarget(familyId)).thenReturn(5L);

    var error = assertThrows(RequestValidationException.class, () -> service.switchOver(familyId));

    assertThat(error.getKey()).isEqualTo("consumerLag");
    assertThat(error.getValue()).isEqualTo("5");
    verify(reindexKafkaConsumerManager).getConsumerLagToTarget(familyId);
    verify(indexFamilyRepository).lockByTenantIdAndVersion("tenant", QueryVersion.V2);
    verify(reindexKafkaConsumerManager, never()).captureTargetOffsets(familyId);
  }

  @Test
  void switchOver_startsCutoverAndRejectsWhenTempConsumerBehindCutoverTarget() {
    var familyId = UUID.randomUUID();
    var family = new IndexFamilyEntity(familyId, "tenant", 2, "tenant_2", IndexFamilyStatus.BUILDING,
      Timestamp.from(Instant.now()), null, null, QueryVersion.V2);

    when(indexFamilyRepository.findById(familyId)).thenReturn(Optional.of(family));
    when(indexFamilyRepository.findActiveByTenantIdAndVersion("tenant", QueryVersion.V2))
      .thenReturn(Optional.empty());
    when(reindexKafkaConsumerManager.getConsumerLagToTarget(familyId)).thenReturn(0L, 3L);

    var error = assertThrows(RequestValidationException.class, () -> service.switchOver(familyId));

    assertThat(error.getKey()).isEqualTo("consumerLag");
    assertThat(error.getValue()).isEqualTo("3");
    verify(indexFamilyRepository).updateStatus(familyId, IndexFamilyStatus.CUTTING_OVER);
    verify(reindexKafkaConsumerManager).captureTargetOffsets(familyId);
    verify(reindexKafkaConsumerManager, never()).stopReindexConsumer(familyId);
  }

  @Test
  void switchOver_completesFromCuttingOverWhenLagIsZero() {
    var familyId = UUID.randomUUID();
    var family = new IndexFamilyEntity(familyId, "tenant", 2, "tenant_2", IndexFamilyStatus.CUTTING_OVER,
      Timestamp.from(Instant.now()), null, null, QueryVersion.V2);

    when(indexFamilyRepository.findById(familyId)).thenReturn(Optional.of(family));
    when(indexFamilyRepository.findActiveByTenantIdAndVersion("tenant", QueryVersion.V2))
      .thenReturn(Optional.empty());
    when(reindexKafkaConsumerManager.getConsumerLagToTarget(familyId)).thenReturn(0L);

    // switchOver will throw from the alias swap (unmocked OpenSearch client) —
    // verify state transitions up to that point
    assertThrows(Exception.class, () -> service.switchOver(familyId));

    verify(indexFamilyRepository).lockByTenantIdAndVersion("tenant", QueryVersion.V2);
    verify(reindexKafkaConsumerManager).getConsumerLagToTarget(familyId);
    verify(reindexKafkaConsumerManager, never()).captureTargetOffsets(familyId);
  }
}
