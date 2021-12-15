package org.folio.search.service.systemuser;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.utils.TestConstants.ENV;
import static org.folio.search.utils.TestConstants.MODULE_NAME;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import feign.FeignException.Forbidden;
import feign.Request;
import java.util.Map;
import org.folio.edge.api.utils.cache.TokenCache;
import org.folio.edge.api.utils.security.SecureStore;
import org.folio.edge.api.utils.security.SecureStore.NotFoundException;
import org.folio.search.client.AuthnClient;
import org.folio.search.client.AuthnClient.UserCredentials;
import org.folio.search.configuration.properties.FolioEnvironment;
import org.folio.search.configuration.properties.ModuleConfigurationProperties;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.utils.types.UnitTest;
import org.folio.spring.integration.XOkapiHeaders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ModuleUserProviderTest {

  public static final String OKAPI_TOKEN = "okapiToken";
  @InjectMocks private ModuleUserProvider moduleUserProvider;
  @Mock private TokenCache tokenCache;
  @Mock private AuthnClient authnClient;
  @Mock private SecureStore secureStore;
  @Mock private ResponseEntity<String> response;

  @Spy private final FolioEnvironment folioEnvironment = FolioEnvironment.of(ENV);
  @Spy private final ModuleConfigurationProperties moduleConfigurationProperties =
    new ModuleConfigurationProperties("http://localhost:8080", MODULE_NAME);

  @Test
  void getOkapiToken_positive_tokenIsCached() {
    when(tokenCache.get(ENV, TENANT_ID, MODULE_NAME)).thenReturn(OKAPI_TOKEN);
    var actual = moduleUserProvider.getOkapiToken(TENANT_ID);
    assertThat(actual).isEqualTo(OKAPI_TOKEN);
    verify(folioEnvironment).getEnvironment();
    verify(moduleConfigurationProperties).getModuleName();
  }

  @Test
  void getOkapiToken_positive_tokenIsNotCached() throws NotFoundException {
    when(tokenCache.get(ENV, TENANT_ID, MODULE_NAME)).thenReturn(null);
    when(secureStore.get(ENV, TENANT_ID, MODULE_NAME)).thenReturn("pwd");
    when(authnClient.getApiKey(UserCredentials.of(MODULE_NAME, "pwd"))).thenReturn(response);
    when(response.getHeaders()).thenReturn(httpHeaders(mapOf(XOkapiHeaders.TOKEN, OKAPI_TOKEN)));

    var actual = moduleUserProvider.getOkapiToken(TENANT_ID);

    assertThat(actual).isEqualTo(OKAPI_TOKEN);
    verify(tokenCache).put(ENV, TENANT_ID, MODULE_NAME, OKAPI_TOKEN);
  }

  @Test
  void getOkapiToken_negative_missingOkapiTokenInHeaders() throws NotFoundException {
    when(tokenCache.get(ENV, TENANT_ID, MODULE_NAME)).thenReturn(null);
    when(secureStore.get(ENV, TENANT_ID, MODULE_NAME)).thenReturn("pwd");
    when(authnClient.getApiKey(UserCredentials.of(MODULE_NAME, "pwd"))).thenReturn(response);
    when(response.getHeaders()).thenReturn(httpHeaders(emptyMap()));

    assertThatThrownBy(() -> moduleUserProvider.getOkapiToken(TENANT_ID))
      .isInstanceOf(SearchServiceException.class)
      .hasMessage("Failed to retrieve Okapi token [username: mod-search, tenant: test_tenant, response: response]");
    verifyNoMoreInteractions(tokenCache);
  }

  @Test
  void getOkapiToken_negative_failedToRetrieveValues() throws NotFoundException {
    when(tokenCache.get(ENV, TENANT_ID, MODULE_NAME)).thenReturn(null);
    when(secureStore.get(ENV, TENANT_ID, MODULE_NAME)).thenThrow(new NotFoundException("not found"));

    assertThatThrownBy(() -> moduleUserProvider.getOkapiToken(TENANT_ID))
      .isInstanceOf(SearchServiceException.class)
      .hasMessage("Failed to get module user settings from secure store [tenantId: test_tenant]");
    verifyNoMoreInteractions(tokenCache);
  }

  @Test
  void getOkapiToken_negative_invalidResponseFromServer() throws NotFoundException {
    var rq = mock(Request.class);
    when(tokenCache.get(ENV, TENANT_ID, MODULE_NAME)).thenReturn(null);
    when(secureStore.get(ENV, TENANT_ID, MODULE_NAME)).thenReturn("pwd");
    when(authnClient.getApiKey(UserCredentials.of(MODULE_NAME, "pwd"))).thenThrow(new Forbidden("forbidden", rq, null));

    assertThatThrownBy(() -> moduleUserProvider.getOkapiToken(TENANT_ID))
      .isInstanceOf(Forbidden.class)
      .hasMessage("forbidden");
    verifyNoMoreInteractions(tokenCache);
  }

  private static HttpHeaders httpHeaders(Map<String, String> headers) {
    var httpHeaders = new HttpHeaders();
    headers.forEach(httpHeaders::add);
    return httpHeaders;
  }
}
