package org.folio.search.service.systemuser;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.edge.api.utils.cache.TokenCache;
import org.folio.edge.api.utils.security.SecureStore;
import org.folio.edge.api.utils.security.SecureStore.NotFoundException;
import org.folio.search.client.AuthnClient;
import org.folio.search.client.AuthnClient.UserCredentials;
import org.folio.search.configuration.properties.FolioEnvironment;
import org.folio.search.configuration.properties.ModuleConfigurationProperties;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.utils.CollectionUtils;
import org.folio.spring.integration.XOkapiHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class ModuleUserProvider {

  private final AuthnClient authnClient;
  private final TokenCache tokenCache;
  private final SecureStore secureStore;
  private final FolioEnvironment folioEnvironment;
  private final ModuleConfigurationProperties moduleConfiguration;

  /**
   * Provides okapi token for tenant.
   *
   * @param tenantId - tenant id as {@link String}
   * @return okapi token as {@link String} value
   */
  public String getOkapiToken(String tenantId) {
    var systemUsername = moduleConfiguration.getModuleName();
    var cachedToken = tokenCache.get(folioEnvironment.getEnvironment(), tenantId, systemUsername);
    if (cachedToken != null) {
      log.debug("Using cached token [tenant: {}]", tenantId);
      return cachedToken;
    }

    log.debug("Fetching okapi token for module user [tenant: {}]", tenantId);
    var password = getModuleUserPassword(tenantId);
    var response = authnClient.getApiKey(UserCredentials.of(systemUsername, password));
    var okapiToken = Optional.ofNullable(response)
      .map(HttpEntity::getHeaders)
      .map(responseHeaders -> responseHeaders.get(XOkapiHeaders.TOKEN))
      .flatMap(CollectionUtils::findFirst)
      .orElseThrow(() -> new SearchServiceException(String.format(
        "Failed to retrieve Okapi token [username: %s, tenant: %s, response: %s]",
        systemUsername, tenantId, response)));
    tokenCache.put(folioEnvironment.getEnvironment(), tenantId, systemUsername, okapiToken);
    return okapiToken;
  }

  private String getModuleUserPassword(String tenantId) {
    try {
      return secureStore.get(folioEnvironment.getEnvironment(), tenantId, moduleConfiguration.getModuleName());
    } catch (NotFoundException e) {
      throw new SearchServiceException(String.format(
        "Failed to get module user settings from secure store [tenantId: %s]", tenantId), e);
    }
  }
}
