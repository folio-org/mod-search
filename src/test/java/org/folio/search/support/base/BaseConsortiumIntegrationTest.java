package org.folio.search.support.base;

import static org.folio.search.support.base.ApiEndpoints.instanceSearchPath;
import static org.folio.search.utils.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.search.utils.TestConstants.MEMBER_TENANT_ID;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.asJsonString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

public abstract class BaseConsortiumIntegrationTest extends BaseIntegrationTest {

  @SneakyThrows
  protected static void removeTenant() {
    removeTenant(TENANT_ID);
    removeTenant(CENTRAL_TENANT_ID);
  }

  @SneakyThrows
  protected static ResultActions doSearchByInstances(String query) {
    return doSearch(instanceSearchPath(), MEMBER_TENANT_ID, Map.of("query", query));
  }

  @SneakyThrows
  protected static ResultActions doSearchByInstances(String query, Boolean expandAll) {
    return doSearch(instanceSearchPath(), MEMBER_TENANT_ID,
      Map.of("query", query, "expandAll", expandAll.toString()));
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @SneakyThrows
  public static ResultActions tryGet(String uri, Object... args) {
    return tryGet(uri, MEMBER_TENANT_ID, args);
  }

  @SneakyThrows
  public static ResultActions tryGet(String uri, String tenantHeader, Object... args) {
    return mockMvc.perform(get(uri, args)
      .headers(defaultHeaders(tenantHeader))
      .accept("application/json;charset=UTF-8"));
  }

  @SneakyThrows
  public static ResultActions doGet(String uri, Object... args) {
    return doGet(uri, MEMBER_TENANT_ID, args);
  }

  @SneakyThrows
  public static ResultActions doGet(String uri, String tenantHeader, Object... args) {
    return tryGet(uri, tenantHeader, args)
      .andExpect(status().isOk());
  }

  @SneakyThrows
  public static ResultActions doGet(MockHttpServletRequestBuilder request) {
    return doGet(request, MEMBER_TENANT_ID);
  }

  @SneakyThrows
  public static ResultActions doGet(MockHttpServletRequestBuilder request, String tenantHeader) {
    return mockMvc.perform(request
        .headers(defaultHeaders(tenantHeader))
        .accept("application/json;charset=UTF-8"))
      .andExpect(status().isOk());
  }

  @SneakyThrows
  public static ResultActions tryPost(String uri, Object body) {
    return tryPost(uri, MEMBER_TENANT_ID, body);
  }

  @SneakyThrows
  public static ResultActions tryPost(String uri, String tenantHeader, Object body) {
    return mockMvc.perform(post(uri)
      .content(asJsonString(body))
      .headers(defaultHeaders(tenantHeader))
      .accept("application/json;charset=UTF-8"));
  }

  @SneakyThrows
  public static ResultActions doPost(String uri, String tenantHeader, Object body) {
    return tryPost(uri, tenantHeader, body)
      .andExpect(status().isOk());
  }
}
