package org.folio.search.service.browse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.model.reindex.IndexFamilyEntity;
import org.folio.search.model.types.IndexFamilyStatus;
import org.folio.search.model.types.QueryVersion;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.IndexFamilyService;
import org.folio.search.service.reindex.jdbc.TenantRepository;
import org.folio.search.service.reindex.jdbc.V2BrowseDirtyIdRepository;
import org.folio.search.utils.V2BrowseIdExtractor;
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
class ScheduledV2BrowseReconcilerServiceTest {

  private static final Map<ResourceType, String> BROWSE_ALIASES = Map.of(
    ResourceType.V2_CONTRIBUTOR, "browse-contributor",
    ResourceType.V2_SUBJECT, "browse-subject",
    ResourceType.V2_CLASSIFICATION, "browse-classification",
    ResourceType.V2_CALL_NUMBER, "browse-call-number"
  );

  @Mock
  private TenantRepository tenantRepository;
  @Mock
  private V2BrowseDirtyIdRepository dirtyIdRepository;
  @Mock
  private IndexFamilyService indexFamilyService;
  @Mock
  private V2BrowseProjectionService browseProjectionService;
  @Mock
  private V2BrowseDirtyIdEnqueueHelper enqueueHelper;
  @Mock
  private SystemUserScopedExecutionService executionService;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private SearchConfigurationProperties configProperties;

  private ScheduledV2BrowseReconcilerService service;

  @BeforeEach
  void setUp() {
    when(configProperties.getIndexing().getV2BrowseReconcilerBatchSize()).thenReturn(2);
    doAnswer(invocation -> invocation.<Callable<?>>getArgument(1).call())
      .when(executionService).executeSystemUserScoped(anyString(), any());
    service = new ScheduledV2BrowseReconcilerService(
      tenantRepository,
      dirtyIdRepository,
      indexFamilyService,
      browseProjectionService,
      enqueueHelper,
      executionService,
      configProperties
    );
  }

  @Test
  void reconcile_claimsAtMostOneBatchPerTenant() {
    var tenant = "tenant-1";

    when(tenantRepository.fetchDataTenantIds()).thenReturn(List.of(tenant));
    when(indexFamilyService.findActiveFamily(tenant, QueryVersion.V2)).thenReturn(Optional.of(activeFamily(tenant)));
    when(indexFamilyService.getAliasName(tenant, QueryVersion.V2)).thenReturn("main-1");
    when(indexFamilyService.getV2BrowseAliasMap(tenant)).thenReturn(BROWSE_ALIASES);
    when(dirtyIdRepository.claimBatch(tenant, 2)).thenReturn(List.of(
      new V2BrowseDirtyIdRepository.DirtyBrowseIdRow("contributor", "contributor-1"),
      new V2BrowseDirtyIdRepository.DirtyBrowseIdRow("subject", "subject-1")
    ));

    service.reconcile();

    final var touched = new V2BrowseIdExtractor.TouchedBrowseIds(Set.of("contributor-1"), Set.of("subject-1"),
      Set.of(), Set.of());
    verify(dirtyIdRepository, times(1)).claimBatch(tenant, 2);
    verify(browseProjectionService).rebuildAll(eq(touched), eq("main-1"), eq(BROWSE_ALIASES));
  }

  @Test
  void reconcile_reenqueuesFailedBatchAndContinuesWithLaterTenants() {
    var firstTenant = "tenant-1";
    var secondTenant = "tenant-2";

    when(tenantRepository.fetchDataTenantIds()).thenReturn(List.of(firstTenant, secondTenant));
    when(indexFamilyService.findActiveFamily(firstTenant, QueryVersion.V2))
      .thenReturn(Optional.of(activeFamily(firstTenant)));
    when(indexFamilyService.findActiveFamily(secondTenant, QueryVersion.V2))
      .thenReturn(Optional.of(activeFamily(secondTenant)));
    when(indexFamilyService.getAliasName(firstTenant, QueryVersion.V2)).thenReturn("main-1");
    when(indexFamilyService.getAliasName(secondTenant, QueryVersion.V2)).thenReturn("main-2");
    when(indexFamilyService.getV2BrowseAliasMap(firstTenant)).thenReturn(BROWSE_ALIASES);
    when(indexFamilyService.getV2BrowseAliasMap(secondTenant)).thenReturn(BROWSE_ALIASES);
    when(dirtyIdRepository.claimBatch(firstTenant, 2)).thenReturn(List.of(
      new V2BrowseDirtyIdRepository.DirtyBrowseIdRow("contributor", "contributor-1")
    ));
    when(dirtyIdRepository.claimBatch(secondTenant, 2)).thenReturn(List.of(
      new V2BrowseDirtyIdRepository.DirtyBrowseIdRow("subject", "subject-2")
    ));
    doThrow(new IllegalStateException("boom"))
      .when(browseProjectionService).rebuildAll(any(), eq("main-1"), anyMap());

    service.reconcile();

    final var firstTouched = new V2BrowseIdExtractor.TouchedBrowseIds(Set.of("contributor-1"), Set.of(), Set.of(),
      Set.of());
    final var secondTouched = new V2BrowseIdExtractor.TouchedBrowseIds(Set.of(), Set.of("subject-2"),
      Set.of(), Set.of());
    verify(enqueueHelper).enqueueTouched(firstTenant, firstTouched);
    verify(browseProjectionService).rebuildAll(eq(secondTouched), eq("main-2"), eq(BROWSE_ALIASES));
  }

  private static IndexFamilyEntity activeFamily(String tenantId) {
    return new IndexFamilyEntity(
      java.util.UUID.randomUUID(),
      1,
      tenantId + "-main-index",
      IndexFamilyStatus.ACTIVE,
      Timestamp.from(Instant.now()),
      null,
      null,
      QueryVersion.V2
    );
  }
}
