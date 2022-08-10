package org.folio.search.controller;

import static org.folio.search.model.service.CqlResourceIdsRequest.HOLDINGS_ID_PATH;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;
import static org.folio.search.utils.TestUtils.randomId;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.TEXT_PLAIN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.OutputStream;
import java.util.List;
import org.folio.search.domain.dto.ResourceId;
import org.folio.search.domain.dto.ResourceIds;
import org.folio.search.model.service.CqlResourceIdsRequest;
import org.folio.search.service.ResourceIdService;
import org.folio.search.service.ResourceIdsStreamHelper;
import org.folio.search.utils.types.UnitTest;
import org.folio.spring.integration.XOkapiHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@UnitTest
@WebMvcTest(HoldingsController.class)
@Import({ApiExceptionHandler.class, ResourceIdsStreamHelper.class})
class HoldingsControllerTest {

  @Autowired
  private MockMvc mockMvc;
  @MockBean
  private ResourceIdService resourceIdService;

  @Test
  void getHoldingsIds_positive() throws Exception {
    var cqlQuery = "id=*";
    var holdingId = randomId();
    var request = CqlResourceIdsRequest.of(INSTANCE_RESOURCE, TENANT_ID, cqlQuery, HOLDINGS_ID_PATH);

    doAnswer(inv -> {
      var out = (OutputStream) inv.getArgument(1);
      var resourceIds = new ResourceIds().totalRecords(1).ids(List.of(new ResourceId().id(holdingId)));
      out.write(OBJECT_MAPPER.writeValueAsBytes(resourceIds));
      return null;
    }).when(resourceIdService).streamResourceIdsAsJson(eq(request), any(OutputStream.class));

    var requestBuilder = get("/search/holdings/ids")
      .queryParam("query", cqlQuery)
      .contentType(APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, TENANT_ID);

    mockMvc.perform(requestBuilder)
      .andExpect(status().isOk())
      .andExpect(content().contentType(APPLICATION_JSON))
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.ids[*].id", is(List.of(holdingId))));
  }

  @Test
  void getHoldingsIdsTextType_positive() throws Exception {
    var cqlQuery = "id=*";
    var holdingId = randomId();
    var request = CqlResourceIdsRequest.of(INSTANCE_RESOURCE, TENANT_ID, cqlQuery, HOLDINGS_ID_PATH);

    doAnswer(inv -> {
      var out = (OutputStream) inv.getArgument(1);
      out.write(OBJECT_MAPPER.writeValueAsBytes(holdingId));
      return null;
    }).when(resourceIdService).streamResourceIdsAsText(eq(request), any(OutputStream.class));

    var requestBuilder = get("/search/holdings/ids")
      .queryParam("query", cqlQuery)
      .contentType(TEXT_PLAIN)
      .header(XOkapiHeaders.TENANT, TENANT_ID);

    mockMvc.perform(requestBuilder)
      .andExpect(status().isOk())
      .andExpect(content().contentType(TEXT_PLAIN))
      .andExpect(content().string(containsString(holdingId)));
  }
}
