package org.folio.search.controller;

import static org.folio.search.utils.JsonUtils.jsonNode;
import static org.folio.search.utils.JsonUtils.jsonObject;
import static org.folio.search.utils.SearchUtils.TENANT_HEADER;
import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;
import static org.folio.search.utils.TestUtils.asJsonString;
import static org.folio.search.utils.TestUtils.randomId;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.folio.search.model.ResourceEventBody;
import org.folio.search.model.rest.request.IndexRequestBody;
import org.folio.search.model.rest.response.FolioCreateIndexResponse;
import org.folio.search.model.rest.response.FolioIndexResourceResponse;
import org.folio.search.model.rest.response.FolioPutMappingResponse;
import org.folio.search.service.IndexService;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

@UnitTest
@WebMvcTest(IndexController.class)
@ExtendWith(SpringExtension.class)
class IndexControllerTest {

  private static final String RESOURCE_NAME = "test-resource";
  private static final String TENANT_ID = "test-tenant";
  public static final String INDEX_NAME = RESOURCE_NAME + "_" + TENANT_ID;

  @Autowired private MockMvc mockMvc;
  @MockBean private IndexService indexService;

  @Test
  void createIndex_positive() throws Exception {
    var requestBuilder = post("/search/index/indices")
      .content(asJsonString(IndexRequestBody.of(RESOURCE_NAME)))
      .contentType(APPLICATION_JSON)
      .header(TENANT_HEADER, TENANT_ID);

    when(indexService.createIndex(RESOURCE_NAME, TENANT_ID))
      .thenReturn(FolioCreateIndexResponse.success(List.of(INDEX_NAME)));

    mockMvc.perform(requestBuilder)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status", is("success")))
      .andExpect(jsonPath("$.indices", is(List.of(INDEX_NAME))));
  }

  @Test
  void updateMappings_positive() throws Exception {
    var requestBuilder = post("/search/index/mappings")
      .content(asJsonString(IndexRequestBody.of(RESOURCE_NAME)))
      .contentType(APPLICATION_JSON)
      .header(TENANT_HEADER, TENANT_ID);

    when(indexService.updateMappings(RESOURCE_NAME, TENANT_ID))
      .thenReturn(FolioPutMappingResponse.success());

    mockMvc.perform(requestBuilder)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status", is("success")));
  }

  @Test
  void indexResources_positive() throws Exception {
    var instanceData = OBJECT_MAPPER.createObjectNode();
    instanceData.put("id", randomId());
    var resourceBody = ResourceEventBody.of("CREATE", TENANT_ID, RESOURCE_NAME,
      jsonObject("id", jsonNode(randomId())));

    var requestBuilder = post("/search/index/resources")
      .content(asJsonString(resourceBody))
      .contentType(APPLICATION_JSON)
      .header(TENANT_HEADER, TENANT_ID);

    when(indexService.indexResources(List.of(resourceBody)))
      .thenReturn(FolioIndexResourceResponse.success());

    mockMvc.perform(requestBuilder)
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.status", is("success")));
  }
}
