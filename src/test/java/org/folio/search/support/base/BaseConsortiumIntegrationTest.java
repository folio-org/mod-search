package org.folio.search.support.base;

import static java.util.Arrays.asList;
import static org.folio.search.support.base.ApiEndpoints.instanceSearchPath;
import static org.folio.search.utils.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.search.utils.TestConstants.MEMBER_TENANT_ID;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.asJsonString;
import static org.folio.search.utils.TestUtils.randomId;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.function.Consumer;
import lombok.SneakyThrows;
import org.folio.search.domain.dto.Instance;
import org.folio.tenant.domain.dto.Parameter;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.junit.jupiter.api.AfterAll;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

public abstract class BaseConsortiumIntegrationTest extends BaseIntegrationTest {

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @SneakyThrows
  protected static void removeTenant() {
    removeTenant(TENANT_ID);
    removeTenant(CENTRAL_TENANT_ID);
  }

  @SneakyThrows
  protected static void setUpTenant(String tenantName, Instance... instances) {
    setUpTenant(tenantName, instanceSearchPath(), () -> { }, asList(instances), instances.length,
      instance -> inventoryApi.createInstance(tenantName, instance));
  }

  @SneakyThrows
  protected static void setUpTenant(String tenantName, int expectedCount, Instance... instances) {
    setUpTenant(tenantName, instanceSearchPath(), () -> { }, asList(instances), expectedCount,
      instance -> inventoryApi.createInstance(tenantName, instance));
  }

  @SneakyThrows
  protected static <T> void setUpTenant(String tenant, String validationPath, Runnable postInitAction,
                                      List<T> records, Integer expectedCount, Consumer<T> consumer) {
    enableTenant(tenant);
    postInitAction.run();
    saveRecords(tenant, validationPath, records, expectedCount, consumer);
  }

  @SneakyThrows
  protected static void enableTenant(String tenant) {
    var tenantAttributes = new TenantAttributes().moduleTo("mod-search");
    tenantAttributes.addParametersItem(new Parameter("centralTenantId").value(CENTRAL_TENANT_ID));

    mockMvc.perform(post("/_/tenant", randomId())
        .content(asJsonString(tenantAttributes))
        .headers(defaultHeaders(tenant))
        .contentType(APPLICATION_JSON))
      .andExpect(status().isNoContent());
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

  @SneakyThrows
  protected static ResultActions doSearchByInstances(String query) {
    return doSearch(instanceSearchPath(), MEMBER_TENANT_ID, query, null, null, null);
  }

  @SneakyThrows
  protected static ResultActions doSearchByInstances(String query, boolean expandAll) {
    return doSearch(instanceSearchPath(), MEMBER_TENANT_ID, query, null, null, expandAll);
  }
}
