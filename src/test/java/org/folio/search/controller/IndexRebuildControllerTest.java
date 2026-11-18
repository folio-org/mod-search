package org.folio.search.controller;

import static org.folio.support.base.ApiEndpoints.itemRecordsReindexFailedPath;
import static org.folio.support.base.ApiEndpoints.itemRecordsReindexFullPath;
import static org.folio.support.base.ApiEndpoints.itemRecordsReindexStatusPath;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.folio.search.domain.dto.IndexRebuildStatusItem;
import org.folio.search.service.indexrebuild.IndexRebuildTarget;
import org.folio.search.service.indexrebuild.derived.DerivedReindexService;
import org.folio.search.service.indexrebuild.records.RecordsReindexService;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.spring.testing.type.UnitTest;
import org.folio.support.config.TestNoOpCacheConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@UnitTest
@WebMvcTest(IndexRebuildController.class)
@Import({ApiExceptionHandler.class, TestNoOpCacheConfig.class})
class IndexRebuildControllerTest {

  private static final String TENANT_ID = "test-tenant";

  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private RecordsReindexService recordsReindexService;
  @MockitoBean
  private DerivedReindexService derivedReindexService;

  @Test
  void reindexItemRecords_positive_withTargetTenant() throws Exception {
    mockMvc.perform(post(itemRecordsReindexFullPath())
        .header(XOkapiHeaders.TENANT, TENANT_ID)
        .contentType(APPLICATION_JSON)
        .content("{\"tenantId\": \"member\"}"))
      .andExpect(status().isOk());

    verify(recordsReindexService).start(IndexRebuildTarget.ITEM, TENANT_ID, "member", null);
  }

  @Test
  void reindexItemRecords_positive_withoutBody() throws Exception {
    mockMvc.perform(post(itemRecordsReindexFullPath()).header(XOkapiHeaders.TENANT, TENANT_ID))
      .andExpect(status().isOk());

    verify(recordsReindexService).start(IndexRebuildTarget.ITEM, TENANT_ID, null, null);
  }

  @Test
  void retryFailedItemRecordsReindex_positive() throws Exception {
    mockMvc.perform(post(itemRecordsReindexFailedPath()).header(XOkapiHeaders.TENANT, TENANT_ID))
      .andExpect(status().isOk());

    verify(recordsReindexService).retryFailed(IndexRebuildTarget.ITEM, TENANT_ID);
  }

  @Test
  void getItemRecordsReindexStatus_positive() throws Exception {
    when(recordsReindexService.getStatus(IndexRebuildTarget.ITEM, TENANT_ID))
      .thenReturn(List.of(new IndexRebuildStatusItem().tenantId(TENANT_ID).status("COMPLETED")));

    mockMvc.perform(get(itemRecordsReindexStatusPath()).header(XOkapiHeaders.TENANT, TENANT_ID))
      .andExpect(status().isOk())
      .andExpect(jsonPath("[0].status", is("COMPLETED")));
  }

  @Test
  void reindexCallNumberRecords_positive() throws Exception {
    mockMvc.perform(post("/search/index/call-number-records/reindex/full").header(XOkapiHeaders.TENANT, TENANT_ID))
      .andExpect(status().isOk());

    verify(derivedReindexService).start(IndexRebuildTarget.CALL_NUMBER, TENANT_ID, null, null);
  }

  @Test
  void retryFailedCallNumberRecordsReindex_positive() throws Exception {
    mockMvc.perform(post("/search/index/call-number-records/reindex/failed").header(XOkapiHeaders.TENANT, TENANT_ID))
      .andExpect(status().isOk());

    verify(derivedReindexService).retryFailed(IndexRebuildTarget.CALL_NUMBER, TENANT_ID);
  }

  @Test
  void getCallNumberRecordsReindexStatus_positive() throws Exception {
    when(derivedReindexService.getStatus(IndexRebuildTarget.CALL_NUMBER, TENANT_ID))
      .thenReturn(List.of(new IndexRebuildStatusItem().tenantId(TENANT_ID).status("IN_PROGRESS")));

    mockMvc.perform(get("/search/index/call-number-records/reindex/status").header(XOkapiHeaders.TENANT, TENANT_ID))
      .andExpect(status().isOk())
      .andExpect(jsonPath("[0].status", is("IN_PROGRESS")));
  }
}
