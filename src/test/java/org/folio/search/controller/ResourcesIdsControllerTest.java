package org.folio.search.controller;

import static org.folio.search.domain.dto.ResourceIdsJob.EntityTypeEnum.INSTANCE;
import static org.folio.search.domain.dto.ResourceIdsJob.StatusEnum.IN_PROGRESS;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.base.ApiEndpoints.resourcesIdsJobPath;
import static org.folio.support.base.ApiEndpoints.resourcesIdsPath;
import static org.folio.support.utils.TestUtils.randomId;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.folio.search.domain.dto.ResourceIdsJob;
import org.folio.search.service.id.ResourceIdsJobService;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.spring.testing.type.UnitTest;
import org.folio.support.config.TestNoOpCacheConfig;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@UnitTest
@WebMvcTest(ResourcesIdsController.class)
@Import({ApiExceptionHandler.class, TestNoOpCacheConfig.class})
class ResourcesIdsControllerTest {

  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private ResourceIdsJobService resourceIdsJobService;

  @Nested
  class GetResourceIdsTest {

    @Test
    void getResourceIds_shouldStreamResourceIds() throws Exception {
      var jobId = "test-job-id";

      when(resourceIdsJobService.streamResourceIdsFromDb(jobId)).thenReturn(null);

      mockMvc.perform(get(resourcesIdsPath(jobId))
          .header(XOkapiHeaders.TENANT, TENANT_ID)
          .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
    }
  }

  @Nested
  class GetJobsTest {

    @Test
    void getIdsJob_shouldReturnResourceIdsJob() throws Exception {
      var jobId = "test-job-id";
      var resourceIdsJob = new ResourceIdsJob().id(jobId).status(IN_PROGRESS);

      when(resourceIdsJobService.getJobById(jobId)).thenReturn(resourceIdsJob);

      mockMvc.perform(get(resourcesIdsJobPath(jobId))
          .header(XOkapiHeaders.TENANT, TENANT_ID)
          .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(jobId))
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }
  }

  @Nested
  class SubmitJobTest {

    @Test
    void submitIdsJob_positive_shouldCreateAndReturnResourceIdsJob() throws Exception {
      var resourceIdsJob = new ResourceIdsJob().id(randomId()).query("id=*").entityType(INSTANCE);

      when(resourceIdsJobService.createStreamJob(any(), eq(TENANT_ID))).thenReturn(resourceIdsJob);

      mockMvc.perform(post(resourcesIdsJobPath())
          .contentType(MediaType.APPLICATION_JSON)
          .header(XOkapiHeaders.TENANT, TENANT_ID)
          .content("{\"query\":\"id=*\",\"entityType\":\"INSTANCE\"}")
          .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(resourceIdsJob.getId()));
    }

    @Test
    void submitIdsJob_negative_bodyIsRequired() throws Exception {
      mockMvc.perform(post(resourcesIdsJobPath())
          .contentType(MediaType.APPLICATION_JSON)
          .header(XOkapiHeaders.TENANT, TENANT_ID)
          .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].message", containsString("Required request body is missing")));
    }

    @Test
    void submitIdsJob_negative_invalidEntityType() throws Exception {
      mockMvc.perform(post(resourcesIdsJobPath())
          .contentType(MediaType.APPLICATION_JSON)
          .header(XOkapiHeaders.TENANT, TENANT_ID)
          .content("{\"query\":\"id=*\",\"entityType\":\"BAD_TYPE\"}")
          .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].message", containsString("Unexpected value 'BAD_TYPE'")));
    }
  }
}
