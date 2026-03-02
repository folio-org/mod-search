package org.folio.search.service.reindex;

import static org.folio.search.model.types.ReindexEntityType.CALL_NUMBER;
import static org.folio.search.model.types.ReindexEntityType.CLASSIFICATION;
import static org.folio.search.model.types.ReindexEntityType.CONTRIBUTOR;
import static org.folio.search.model.types.ReindexEntityType.HOLDINGS;
import static org.folio.search.model.types.ReindexEntityType.INSTANCE;
import static org.folio.search.model.types.ReindexEntityType.ITEM;
import static org.folio.search.model.types.ReindexEntityType.SUBJECT;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.folio.search.domain.dto.FolioCreateIndexResponse;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.domain.dto.IndexSettings;
import org.folio.search.model.types.ResourceType;
import org.folio.search.repository.PrimaryResourceRepository;
import org.folio.search.service.IndexService;
import org.folio.search.service.reindex.jdbc.CallNumberRepository;
import org.folio.search.service.reindex.jdbc.ClassificationRepository;
import org.folio.search.service.reindex.jdbc.ContributorRepository;
import org.folio.search.service.reindex.jdbc.HoldingRepository;
import org.folio.search.service.reindex.jdbc.ItemRepository;
import org.folio.search.service.reindex.jdbc.ReindexJdbcRepository;
import org.folio.search.service.reindex.jdbc.SubjectRepository;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ReindexCommonServiceTest {

  private ReindexCommonService service;

  @Mock
  private ReindexJdbcRepository instanceRepository;
  @Mock
  private HoldingRepository holdingRepository;
  @Mock
  private ItemRepository itemRepository;
  @Mock
  private SubjectRepository subjectRepository;
  @Mock
  private ContributorRepository contributorRepository;
  @Mock
  private ClassificationRepository classificationRepository;
  @Mock
  private CallNumberRepository callNumberRepository;
  @Mock
  private IndexService indexService;
  @Mock
  private PrimaryResourceRepository resourceRepository;

  @BeforeEach
  void setUp() {
    when(instanceRepository.entityType()).thenReturn(INSTANCE);
    when(holdingRepository.entityType()).thenReturn(HOLDINGS);
    when(itemRepository.entityType()).thenReturn(ITEM);
    when(subjectRepository.entityType()).thenReturn(SUBJECT);
    when(contributorRepository.entityType()).thenReturn(CONTRIBUTOR);
    when(classificationRepository.entityType()).thenReturn(CLASSIFICATION);
    when(callNumberRepository.entityType()).thenReturn(CALL_NUMBER);

    List<ReindexJdbcRepository> repositories = List.of(
      instanceRepository, holdingRepository, itemRepository,
      subjectRepository, contributorRepository, classificationRepository, callNumberRepository
    );

    service = new ReindexCommonService(repositories, indexService, resourceRepository);
  }

  @Test
  void deleteAllRecords_withNullTenantId_shouldTruncateAllTables() {
    // Act
    service.deleteAllRecords(null);

    // Assert - verify truncate is called for all entity types
    verify(instanceRepository).truncate();
    verify(holdingRepository).truncate();
    verify(itemRepository).truncate();
    verify(subjectRepository).truncate();
    verify(contributorRepository).truncate();
    verify(classificationRepository).truncate();
    verify(callNumberRepository).truncate();

    // Verify truncateStaging is NOT called
    verify(instanceRepository, times(0)).truncateStaging();
  }

  @Test
  void deleteAllRecords_withTenantId_shouldTruncateOnlyStagingTables() {
    // Act
    service.deleteAllRecords(TENANT_ID);

    // Assert - verify truncateStaging is called for all entity types
    verify(instanceRepository).truncateStaging();
    verify(holdingRepository).truncateStaging();
    verify(itemRepository).truncateStaging();
    verify(subjectRepository).truncateStaging();
    verify(contributorRepository).truncateStaging();
    verify(classificationRepository).truncateStaging();
    verify(callNumberRepository).truncateStaging();

    // Verify regular truncate is NOT called
    verify(instanceRepository, times(0)).truncate();
  }

  @Test
  void deleteRecordsByTenantId_shouldDeleteInCorrectOrder() {
    // Act
    service.deleteRecordsByTenantId(TENANT_ID);

    // Assert - verify order: child entities first, then parent entities
    var inOrder = inOrder(
      subjectRepository, contributorRepository, classificationRepository, callNumberRepository,
      itemRepository, holdingRepository, instanceRepository
    );

    // Child entities (relationships) first
    inOrder.verify(subjectRepository).deleteByTenantId(TENANT_ID);
    inOrder.verify(contributorRepository).deleteByTenantId(TENANT_ID);
    inOrder.verify(classificationRepository).deleteByTenantId(TENANT_ID);
    inOrder.verify(callNumberRepository).deleteByTenantId(TENANT_ID);

    // Then parent entities
    inOrder.verify(itemRepository).deleteByTenantId(TENANT_ID);
    inOrder.verify(holdingRepository).deleteByTenantId(TENANT_ID);
    inOrder.verify(instanceRepository).deleteByTenantId(TENANT_ID);
  }

  @Test
  void deleteInstanceDocumentsByTenantId_shouldCallDeleteOnRepository() {
    // Arrange
    when(resourceRepository.deleteConsortiumDocumentsByTenantId(ResourceType.INSTANCE, TENANT_ID))
      .thenReturn(new FolioIndexOperationResponse());

    // Act
    service.deleteInstanceDocumentsByTenantId(TENANT_ID);

    // Assert
    verify(resourceRepository).deleteConsortiumDocumentsByTenantId(ResourceType.INSTANCE, TENANT_ID);
  }

  @Test
  void deleteInstanceDocumentsByTenantId_whenExceptionOccurs_shouldLogAndContinue() {
    // Arrange
    when(resourceRepository.deleteConsortiumDocumentsByTenantId(any(), any()))
      .thenThrow(new RuntimeException("Test exception"));

    // Act - should not throw exception
    assertDoesNotThrow(() -> service.deleteInstanceDocumentsByTenantId(TENANT_ID));
  }

  @Test
  void recreateIndex_shouldCallIndexService() {
    // Arrange
    var indexSettings = new IndexSettings();
    doNothing().when(indexService).dropIndex(any(), any());
    when(indexService.createIndex(any(), any(), any())).thenReturn(new FolioCreateIndexResponse());

    // Act
    service.recreateIndex(INSTANCE, TENANT_ID, indexSettings);

    // Assert
    verify(indexService).dropIndex(ResourceType.INSTANCE, TENANT_ID);
    verify(indexService).createIndex(ResourceType.INSTANCE, TENANT_ID, indexSettings);
  }

  @Test
  void recreateIndex_withNullSettings_shouldCallIndexServiceWithoutSettings() {
    // Arrange
    doNothing().when(indexService).dropIndex(any(), any());
    when(indexService.createIndex(any(), any(), any())).thenReturn(new FolioCreateIndexResponse());

    // Act
    service.recreateIndex(INSTANCE, TENANT_ID, null);

    // Assert
    verify(indexService).dropIndex(ResourceType.INSTANCE, TENANT_ID);
    verify(indexService).createIndex(ResourceType.INSTANCE, TENANT_ID, null);
  }

  @Test
  void ensureIndexExists_shouldCallCreateIndexIfNotExist() {
    // Arrange
    var indexSettings = new IndexSettings();
    doNothing().when(indexService).createIndexIfNotExist(any(), any(), any());

    // Act
    service.ensureIndexExists(INSTANCE, TENANT_ID, indexSettings);

    // Assert
    verify(indexService).createIndexIfNotExist(ResourceType.INSTANCE, TENANT_ID, indexSettings);
  }

  @Test
  void ensureIndexExists_withNullSettings_shouldCallCreateIndexIfNotExistWithoutSettings() {
    // Arrange
    doNothing().when(indexService).createIndexIfNotExist(any(), any());

    // Act
    service.ensureIndexExists(INSTANCE, TENANT_ID, null);

    // Assert
    verify(indexService).createIndexIfNotExist(ResourceType.INSTANCE, TENANT_ID);
  }

  @Test
  void ensureIndexExists_whenExceptionOccurs_shouldNotPropagateException() {
    // Arrange
    doThrow(new RuntimeException("Test exception"))
      .when(indexService).createIndexIfNotExist(any(), any());

    // Act - should not throw exception
    service.ensureIndexExists(INSTANCE, TENANT_ID, null);

    // Assert
    verify(indexService).createIndexIfNotExist(ResourceType.INSTANCE, TENANT_ID);
  }

  @Test
  void recreateIndex_whenExceptionOccurs_shouldNotPropagateException() {
    // Arrange
    doThrow(new RuntimeException("Test exception"))
      .when(indexService).dropIndex(any(ResourceType.class), any());

    // Act - should not throw exception
    service.recreateIndex(INSTANCE, TENANT_ID, null);

    // Assert
    verify(indexService).dropIndex(ResourceType.INSTANCE, TENANT_ID);
  }
}

