package org.folio.search.service.scheduled;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.service.InstanceChildrenResourceService;
import org.folio.search.service.ResourceService;
import org.folio.search.service.ScheduledInstanceSubResourcesService;
import org.folio.search.service.reindex.jdbc.ItemRepository;
import org.folio.search.service.reindex.jdbc.MergeInstanceRepository;
import org.folio.search.service.reindex.jdbc.SubResourceResult;
import org.folio.search.service.reindex.jdbc.SubResourcesLockRepository;
import org.folio.search.service.reindex.jdbc.SubjectRepository;
import org.folio.search.service.reindex.jdbc.TenantRepository;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ScheduledInstanceSubResourcesServiceTest {

  private @Mock ResourceService resourceService;
  private @Mock TenantRepository tenantRepository;
  private @Mock SubResourcesLockRepository subResourcesLockRepository;
  private @Mock SystemUserScopedExecutionService executionService;
  private @Mock InstanceChildrenResourceService instanceChildrenResourceService;
  private @Mock SubjectRepository subjectRepository;
  private @Mock MergeInstanceRepository instanceRepository;
  private @Mock ItemRepository itemRepository;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private SearchConfigurationProperties indexingConfig;
  private ScheduledInstanceSubResourcesService service;
  private Timestamp timestamp;

  @BeforeEach
  void setUp() {
    when(indexingConfig.getIndexing().getSubResourceBatchSize()).thenReturn(3);
    when(subjectRepository.entityType()).thenReturn(ReindexEntityType.SUBJECT);

    service = new ScheduledInstanceSubResourcesService(
      resourceService,
      tenantRepository,
      List.of(subjectRepository),
      subResourcesLockRepository,
      executionService,
      instanceRepository,
      itemRepository,
      indexingConfig
    );
    service.setInstanceChildrenResourceService(instanceChildrenResourceService);

    timestamp = new Timestamp(System.currentTimeMillis());
  }

  @Test
  void persistChildren_ShouldProcessSubResources() {
    // Arrange
    var tenantId = "testTenant";
    doAnswer(invocation -> invocation.<Callable<?>>getArgument(1).call())
      .when(executionService).executeSystemUserScoped(anyString(), any());
    when(subResourcesLockRepository.lockSubResource(any(), any())).thenReturn(Optional.of(timestamp));
    when(tenantRepository.fetchDataTenantIds()).thenReturn(List.of(tenantId));
    mockSubResourceResult(tenantId, timestamp);

    // Act
    service.persistChildren();

    // Assert
    verify(instanceRepository).fetchByTimestamp(tenantId, timestamp);
    verify(subjectRepository).fetchByTimestamp(tenantId, timestamp, 3);
    verify(resourceService).indexResources(anyList());
    verify(subResourcesLockRepository).unlockSubResource(eq(ReindexEntityType.SUBJECT), any(), eq(tenantId));
  }

  @Test
  void persistChildren_ShouldProcessSubResourcesSizeEqualsBatchSize() {
    // Arrange
    var tenantId = "testTenant";
    doAnswer(invocation -> invocation.<Callable<?>>getArgument(1).call())
      .when(executionService).executeSystemUserScoped(anyString(), any());
    when(subResourcesLockRepository.lockSubResource(any(), any())).thenReturn(Optional.of(timestamp));
    when(tenantRepository.fetchDataTenantIds()).thenReturn(List.of(tenantId));
    when(subjectRepository.fetchByTimestamp(tenantId, timestamp, 3))
      .thenReturn(new SubResourceResult(List.of(Map.of("id", "1", "tenantId", tenantId),
        Map.of("id", "2", "tenantId", tenantId),
        Map.of("id", "3", "tenantId", tenantId)), timestamp));
    when(instanceRepository.fetchByTimestamp(tenantId, timestamp))
      .thenReturn(new SubResourceResult(List.of(Map.of("id", "2", "tenantId", tenantId, "isDeleted", true)), null));
    when(itemRepository.fetchByTimestamp(tenantId, timestamp))
      .thenReturn(new SubResourceResult(List.of(Map.of("id", "3", "tenantId", tenantId)), null));

    // Act
    service.persistChildren();

    // Assert
    verify(instanceRepository).fetchByTimestamp(tenantId, timestamp);
    verify(subjectRepository).fetchByTimestamp(tenantId, timestamp, 3);
    verify(resourceService).indexResources(anyList());
    verify(subResourcesLockRepository).unlockSubResource(eq(ReindexEntityType.SUBJECT), any(), eq(tenantId));
  }

  @Test
  void persistChildren_ShouldSkipProcessingWhenNoTimestamp() {
    // Arrange
    var tenantId = "testTenant";
    when(tenantRepository.fetchDataTenantIds()).thenReturn(List.of(tenantId));

    // Act
    service.persistChildren();

    // Assert
    verify(subjectRepository, never()).fetchByTimestamp(anyString(), any());
    verify(resourceService, never()).indexResources(anyList());
    verify(subResourcesLockRepository, never()).unlockSubResource(any(), any(), any());
  }

  @Test
  void persistChildren_ShouldHandleMultipleTenants() {
    // Arrange
    var tenantIds = List.of("tenant1", "tenant2");
    doAnswer(invocation -> invocation.<Callable<?>>getArgument(1).call())
      .when(executionService).executeSystemUserScoped(anyString(), any());
    when(subResourcesLockRepository.lockSubResource(any(), any())).thenReturn(Optional.of(timestamp));
    when(tenantRepository.fetchDataTenantIds()).thenReturn(tenantIds);
    mockSubResourceResult(tenantIds.get(0), timestamp);
    mockSubResourceResult(tenantIds.get(tenantIds.size() - 1), timestamp);

    // Act
    service.persistChildren();

    // Assert
    verify(subjectRepository, times(2)).fetchByTimestamp(anyString(), any(), anyInt());
    verify(resourceService, times(2)).indexResources(anyList());
    verify(subResourcesLockRepository, times(6)).unlockSubResource(any(), any(), any());
  }

  @Test
  void persistChildren_ShouldHandleEmptyTenantList() {
    // Arrange
    when(tenantRepository.fetchDataTenantIds()).thenReturn(List.of());

    // Act
    service.persistChildren();

    // Assert
    verify(subjectRepository, never()).fetchByTimestamp(anyString(), any());
    verify(resourceService, never()).indexResources(anyList());
    verify(subResourcesLockRepository, never()).unlockSubResource(any(), any(), any());
  }

  private void mockSubResourceResult(String tenantId, Timestamp timestamp) {
    when(subjectRepository.fetchByTimestamp(tenantId, timestamp, 3))
      .thenReturn(new SubResourceResult(List.of(Map.of("id", "1", "tenantId", tenantId)), null));
    when(instanceRepository.fetchByTimestamp(tenantId, timestamp))
      .thenReturn(new SubResourceResult(List.of(Map.of("id", "2", "tenantId", tenantId)), null));
    when(itemRepository.fetchByTimestamp(tenantId, timestamp))
      .thenReturn(new SubResourceResult(List.of(Map.of("id", "3", "tenantId", tenantId)), null));
  }
}
