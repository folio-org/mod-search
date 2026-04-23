package org.folio.search.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityNotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.types.QueryVersion;
import org.folio.search.service.IndexFamilyService;
import org.folio.search.service.QueryVersionResolver;
import org.folio.search.service.browse.V2BrowseFullRebuildService;
import org.folio.search.service.reindex.StreamingReindexService;
import org.folio.search.service.reindex.V2IndexFamilyRuntimeStatusService;
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
  private V2IndexFamilyRuntimeStatusService v2IndexFamilyRuntimeStatusService;
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
      .andExpect(jsonPath("$.queryVersion", is("2")))
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

  @Test
  void getFamilyStatus_returnsTrackedRuntimeBreakdown() throws Exception {
    var familyId = UUID.randomUUID();
    var response = new V2IndexFamilyRuntimeStatusService.IndexFamilyRuntimeStatusResponse(
      familyId.toString(),
      "2",
      "STAGED",
      "CATCHING_UP",
      "2026-04-22T20:08:55.976Z",
      new V2IndexFamilyRuntimeStatusService.Summary(
        UUID.randomUUID().toString(),
        true,
        true,
        true,
        158822L,
        null,
        null),
      new V2IndexFamilyRuntimeStatusService.Details(
        new V2IndexFamilyRuntimeStatusService.Phases(
          new V2IndexFamilyRuntimeStatusService.PhaseBlock(
            "COMPLETED", "2026-04-22T20:06:17.382Z", "2026-04-22T20:07:57.810Z", 100428L, java.util.Map.of()),
          new V2IndexFamilyRuntimeStatusService.PhaseBlock(
            "COMPLETED", "2026-04-22T20:08:03.300Z", "2026-04-22T20:08:16.506Z", 13206L, java.util.Map.of()),
          new V2IndexFamilyRuntimeStatusService.PhaseBlock(
            "IN_PROGRESS", "2026-04-22T20:08:16.511Z", null, 39465L, java.util.Map.of("consumerLagToTarget", 0)),
          new V2IndexFamilyRuntimeStatusService.PhaseBlock("PENDING", null, null, null, java.util.Map.of())),
        new V2IndexFamilyRuntimeStatusService.Resources(
          new V2IndexFamilyRuntimeStatusService.ResourceBlock(
            "COMPLETED", "2026-04-22T20:06:17.382Z", "2026-04-22T20:07:10.000Z", 52618L,
            12345L, 0L, 25L, 49800L, 18500L, 7200L, 21100L, 1992.0, 493.8),
          new V2IndexFamilyRuntimeStatusService.ResourceBlock(
            "COMPLETED", "2026-04-22T20:07:10.001Z", "2026-04-22T20:07:35.000Z", 24999L,
            9000L, 0L, 18L, 22000L, 3200L, 2100L, 15400L, 1222.2, 500.0),
          new V2IndexFamilyRuntimeStatusService.ResourceBlock(
            "COMPLETED", "2026-04-22T20:07:35.001Z", "2026-04-22T20:07:57.800Z", 22799L,
            11000L, 0L, 22L, 20400L, 4100L, 2600L, 13200L, 927.3, 500.0))));

    when(v2IndexFamilyRuntimeStatusService.getStatus(familyId)).thenReturn(response);

    mockMvc.perform(get("/search/index/families/{id}/status", familyId)
        .header(XOkapiHeaders.TENANT, TENANT_ID))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.familyId", is(familyId.toString())))
      .andExpect(jsonPath("$.currentPhase", is("CATCHING_UP")))
      .andExpect(jsonPath("$.summary.trackedInMemory", is(true)))
      .andExpect(jsonPath("$.details.resources.instance.totalOsBulkMs", is(21100)));
  }

  @Test
  void getFamilyStatus_returnsBadRequestForLegacyFamily() throws Exception {
    var familyId = UUID.randomUUID();

    when(v2IndexFamilyRuntimeStatusService.getStatus(familyId))
      .thenThrow(new RequestValidationException("V2 only", "queryVersion", "1"));

    mockMvc.perform(get("/search/index/families/{id}/status", familyId)
        .header(XOkapiHeaders.TENANT, TENANT_ID))
      .andExpect(status().isBadRequest());
  }

  @Test
  void getFamilyStatus_returnsNotFoundForMissingFamily() throws Exception {
    var familyId = UUID.randomUUID();

    when(v2IndexFamilyRuntimeStatusService.getStatus(familyId))
      .thenThrow(new EntityNotFoundException("Index family not found"));

    mockMvc.perform(get("/search/index/families/{id}/status", familyId)
        .header(XOkapiHeaders.TENANT, TENANT_ID))
      .andExpect(status().isNotFound());
  }
}
