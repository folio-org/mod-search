package org.folio.search.service.browse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.client.RequestOptions.DEFAULT;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.folio.search.model.reindex.IndexFamilyEntity;
import org.folio.search.model.types.IndexFamilyStatus;
import org.folio.search.model.types.QueryVersion;
import org.folio.search.model.types.ResourceType;
import org.folio.search.repository.IndexRepository;
import org.folio.search.service.IndexFamilyService;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.index.reindex.BulkByScrollResponse;

@UnitTest
@ExtendWith(MockitoExtension.class)
class V2BrowseFullRebuildServiceTest {

  private static final UUID FAMILY_ID = UUID.randomUUID();
  private static final Map<ResourceType, String> BROWSE_INDICES = Map.of(
    ResourceType.V2_CONTRIBUTOR, "browse-contributor",
    ResourceType.V2_SUBJECT, "browse-subject",
    ResourceType.V2_CLASSIFICATION, "browse-classification",
    ResourceType.V2_CALL_NUMBER, "browse-call-number"
  );

  @Mock
  private IndexFamilyService indexFamilyService;
  @Mock
  private V2BrowseProjectionService browseProjectionService;
  @Mock
  private RestHighLevelClient elasticsearchClient;
  @Mock
  private IndexRepository indexRepository;
  @Mock
  private Executor browseRebuildExecutor;

  @InjectMocks
  private V2BrowseFullRebuildService service;

  @BeforeEach
  void setUp() throws Exception {
    var family = new IndexFamilyEntity(
      FAMILY_ID,
      "diku",
      3,
      "main-index",
      IndexFamilyStatus.ACTIVE,
      Timestamp.from(Instant.now()),
      null,
      null,
      QueryVersion.V2
    );
    when(indexFamilyService.findById(FAMILY_ID)).thenReturn(Optional.of(family));
    when(indexFamilyService.getV2BrowsePhysicalIndexMap("diku", 3)).thenReturn(BROWSE_INDICES);
    when(elasticsearchClient.deleteByQuery(any(), eq(DEFAULT))).thenReturn(mock(BulkByScrollResponse.class));
  }

  @Test
  void rebuildBrowse_clearsAllBrowseIndicesAndTriggersSinglePassProjection() throws Exception {
    service.rebuildBrowse(FAMILY_ID);

    verify(elasticsearchClient, times(BROWSE_INDICES.size())).deleteByQuery(any(), eq(DEFAULT));
    verify(browseProjectionService).rebuildFull("main-index", BROWSE_INDICES);
  }

  @Test
  void rebuildBrowseAsync_usesDedicatedExecutor() throws Exception {
    doAnswer(invocation -> {
      invocation.<Runnable>getArgument(0).run();
      return null;
    }).when(browseRebuildExecutor).execute(any(Runnable.class));

    service.rebuildBrowseAsync(FAMILY_ID);

    verify(browseRebuildExecutor).execute(any(Runnable.class));
    verify(browseProjectionService).rebuildFull("main-index", BROWSE_INDICES);
  }
}
