package org.folio.search.configuration;

import static org.folio.edge.api.utils.Constants.PROP_SECURE_STORE_TYPE;
import static org.folio.edge.api.utils.security.AwsParamStore.PROP_ECS_CREDENTIALS_ENDPOINT;
import static org.folio.edge.api.utils.security.AwsParamStore.PROP_ECS_CREDENTIALS_PATH;
import static org.folio.edge.api.utils.security.AwsParamStore.PROP_REGION;
import static org.folio.edge.api.utils.security.AwsParamStore.PROP_USE_IAM;
import static org.folio.edge.api.utils.security.EphemeralStore.PROP_TENANTS;
import static org.folio.edge.api.utils.security.SecureStoreFactory.getSecureStore;
import static org.folio.edge.api.utils.security.VaultStore.PROP_KEYSTORE_JKS_FILE;
import static org.folio.edge.api.utils.security.VaultStore.PROP_KEYSTORE_PASS;
import static org.folio.edge.api.utils.security.VaultStore.PROP_SSL_PEM_FILE;
import static org.folio.edge.api.utils.security.VaultStore.PROP_TRUSTSTORE_JKS_FILE;
import static org.folio.edge.api.utils.security.VaultStore.PROP_VAULT_ADDRESS;
import static org.folio.edge.api.utils.security.VaultStore.PROP_VAULT_TOKEN;
import static org.folio.edge.api.utils.security.VaultStore.PROP_VAULT_USE_SSL;

import java.util.Properties;
import java.util.StringJoiner;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections.CollectionUtils;
import org.folio.edge.api.utils.cache.TokenCache;
import org.folio.edge.api.utils.security.SecureStore;
import org.folio.search.configuration.properties.ModuleUserConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Log4j2
@Configuration
@RequiredArgsConstructor
public class ModuleUserConfiguration {

  private final ModuleUserConfigurationProperties configurationProperties;

  @Bean
  public TokenCache tokenCache() {
    log.info("Using token cache TTL (ms): {}", configurationProperties.getCacheTtlMs());
    log.info("Using failure token cache TTL (ms): {}", configurationProperties.getFailureCacheTtlMs());
    log.info("Using token cache capacity: {}", configurationProperties.getCacheCapacity());
    return TokenCache.initialize(
      configurationProperties.getCacheTtlMs(),
      configurationProperties.getFailureCacheTtlMs(),
      configurationProperties.getCacheCapacity());
  }

  @Bean
  public SecureStore secureStore() {
    return getSecureStore(configurationProperties.getStoreType().getValue(), getConfigurationAsProperties());
  }

  private Properties getConfigurationAsProperties() {
    switch (configurationProperties.getStoreType()) {
      case AWS_SSM:
        return getAwsProperties();
      case VAULT:
        return getVaultProperties();
      case EPHEMERAL:
      default:
        return getEphemeralProperties();
    }
  }

  private Properties getAwsProperties() {
    var properties = new Properties();
    var awsConfig = configurationProperties.getAwsSsm();

    setProperty(properties, PROP_SECURE_STORE_TYPE, configurationProperties.getStoreType());
    setProperty(properties, PROP_REGION, awsConfig.getRegion());
    setProperty(properties, PROP_USE_IAM, awsConfig.getUseIam());
    setProperty(properties, PROP_ECS_CREDENTIALS_PATH, awsConfig.getEcsCredentialsPath());
    setProperty(properties, PROP_ECS_CREDENTIALS_ENDPOINT, awsConfig.getEcsCredentialsEndpoint());
    return properties;
  }

  protected Properties getVaultProperties() {
    var properties = new Properties();
    setProperty(properties, PROP_SECURE_STORE_TYPE, configurationProperties.getStoreType());

    var vaultConfig = configurationProperties.getVault();
    setProperty(properties, PROP_VAULT_TOKEN, vaultConfig.getToken());
    setProperty(properties, PROP_VAULT_ADDRESS, vaultConfig.getAddress());
    setProperty(properties, PROP_VAULT_USE_SSL, vaultConfig.getEnableSsl());
    setProperty(properties, PROP_SSL_PEM_FILE, vaultConfig.getPemFilePath());
    setProperty(properties, PROP_KEYSTORE_JKS_FILE, vaultConfig.getKeystoreFilePath());
    setProperty(properties, PROP_TRUSTSTORE_JKS_FILE, vaultConfig.getTruststoreFilePath());
    setProperty(properties, PROP_KEYSTORE_PASS, vaultConfig.getKeystorePassword());
    return properties;
  }

  private Properties getEphemeralProperties() {
    var properties = new Properties();
    setProperty(properties, PROP_SECURE_STORE_TYPE, configurationProperties.getStoreType());

    var ephemeralConfig = configurationProperties.getEphemeral();
    var tenants = new StringJoiner(",");

    var credentialsString = ephemeralConfig.getCredentials();
    if (CollectionUtils.isNotEmpty(credentialsString)) {
      for (var value : credentialsString) {
        var values = value.split(":");
        var tenantId = values[0];
        tenants.add(tenantId);
        setProperty(properties, tenantId, values[1] + "," + values[2]);
      }
    }

    properties.setProperty(PROP_TENANTS, tenants.toString());
    return properties;
  }

  private static void setProperty(Properties properties, String key, Object value) {
    if (value != null && !value.toString().isBlank()) {
      properties.setProperty(key, value.toString());
    }
  }
}

