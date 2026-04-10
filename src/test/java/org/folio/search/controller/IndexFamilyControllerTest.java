package org.folio.search.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.UUID;
import org.folio.search.model.types.QueryVersion;
import org.folio.search.service.IndexFamilyService;
import org.folio.search.service.QueryVersionResolver;
import org.folio.search.service.browse.V2BrowseFullRebuildService;
import org.folio.search.service.reindex.StreamingReindexService;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@UnitTest
@WebMvcTest(IndexFamilyController.class)
@Import(ApiExceptionHandler.class)
class IndexFamilyControllerTest {

  private static final String TENANT_ID = "test-tenant";

  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private IndexFamilyService indexFamilyService;
  @MockitoBean
  private StreamingReindexService streamingReindexService;
  @MockitoBean
  private QueryVersionResolver queryVersionResolver;
  @MockitoBean
  private V2BrowseFullRebuildService browseFullRebuildService;
  @MockitoBean
  private FolioExecutionContext context;

  @Test
  void startStreamingReindex_returnsJobAndFamilyId() throws Exception {
    var jobId = UUID.randomUUID();
    var familyId = UUID.randomUUID();

    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(streamingReindexService.startStreamingReindex(eq(TENANT_ID), eq(QueryVersion.V2), isNull()))
      .thenReturn(new StreamingReindexService.StreamingReindexJob(jobId, familyId));

    mockMvc.perform(post("/search/index/reindex/stream")
        .header(XOkapiHeaders.TENANT, TENANT_ID))
      .andExpect(status().isAccepted())
      .andExpect(jsonPath("$.jobId", is(jobId.toString())))
      .andExpect(jsonPath("$.familyId", is(familyId.toString())))
      .andExpect(jsonPath("$.status", is("ACCEPTED")));
  }

  @Test
  void listQueryVersions_returnsComputedVersionState() throws Exception {
    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(queryVersionResolver.getDefaultVersion()).thenReturn("1");
    when(indexFamilyService.findActiveFamily(TENANT_ID, QueryVersion.V1)).thenReturn(Optional.empty());
    when(indexFamilyService.findActiveFamily(TENANT_ID, QueryVersion.V2)).thenReturn(Optional.empty());

    mockMvc.perform(get("/search/index/query-versions")
        .header(XOkapiHeaders.TENANT, TENANT_ID))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.defaultVersion", is("1")));
  }
}
