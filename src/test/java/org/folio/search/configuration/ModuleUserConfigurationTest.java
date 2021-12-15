package org.folio.search.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.utils.TestConstants.ENV;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.folio.edge.api.utils.security.EphemeralStore;
import org.folio.edge.api.utils.security.SecureStore.NotFoundException;
import org.folio.search.configuration.properties.ModuleUserConfigurationProperties;
import org.folio.search.configuration.properties.ModuleUserConfigurationProperties.AwsConfig;
import org.folio.search.configuration.properties.ModuleUserConfigurationProperties.EphemeralConfig;
import org.folio.search.configuration.properties.ModuleUserConfigurationProperties.ModuleUserProviderType;
import org.folio.search.configuration.properties.ModuleUserConfigurationProperties.VaultConfig;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ModuleUserConfigurationTest {

  @InjectMocks private ModuleUserConfiguration moduleUserConfiguration;
  @Mock private ModuleUserConfigurationProperties configurationProperties;

  @Test
  void tokenCache_positive() {
    when(configurationProperties.getCacheTtlMs()).thenReturn(10L);
    when(configurationProperties.getFailureCacheTtlMs()).thenReturn(10L);
    when(configurationProperties.getCacheCapacity()).thenReturn(100);

    var tokenCache = moduleUserConfiguration.tokenCache();
    assertThat(tokenCache).isNotNull();
  }

  @Test
  void secureStore_positive_ephemeral() throws NotFoundException {
    var ephemeralConfig = mock(EphemeralConfig.class);
    when(configurationProperties.getStoreType()).thenReturn(ModuleUserProviderType.EPHEMERAL);
    when(configurationProperties.getEphemeral()).thenReturn(ephemeralConfig);
    when(ephemeralConfig.getCredentials()).thenReturn(List.of(TENANT_ID + ":mod-search:qwerty"));

    var secureStore = moduleUserConfiguration.secureStore();
    assertThat(secureStore).isNotNull().isInstanceOf(EphemeralStore.class);
    assertThat(secureStore.get(ENV, TENANT_ID, "mod-search")).isEqualTo("qwerty");
  }

  @Test
  void secureStore_positive_ephemeralWithEmptyCredentials() {
    var ephemeralConfig = mock(EphemeralConfig.class);
    when(configurationProperties.getStoreType()).thenReturn(ModuleUserProviderType.EPHEMERAL);
    when(configurationProperties.getEphemeral()).thenReturn(ephemeralConfig);
    when(ephemeralConfig.getCredentials()).thenReturn(null);

    var secureStore = moduleUserConfiguration.secureStore();
    assertThat(secureStore).isNotNull().isInstanceOf(EphemeralStore.class);
    assertThatThrownBy(() -> secureStore.get(ENV, TENANT_ID, "mod-search"))
      .isInstanceOf(NotFoundException.class)
      .hasMessage("Nothing associated w/ key: test_tenant_mod-search");
  }

  @Test
  void secureStore_negative_aws() {
    var awsSsmConfig = mock(AwsConfig.class);

    when(configurationProperties.getStoreType()).thenReturn(ModuleUserProviderType.AWS_SSM);
    when(configurationProperties.getAwsSsm()).thenReturn(awsSsmConfig);

    when(awsSsmConfig.getEcsCredentialsPath()).thenReturn("/path");
    when(awsSsmConfig.getRegion()).thenReturn("");

    // this unit test requires correct aws configuration to pass through, so this assertion verifies that
    // properties are passed correctly to the secure store factory.
    assertThatThrownBy(() -> moduleUserConfiguration.secureStore())
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("URI is not absolute");
  }

  @Test
  void secureStore_negative_value() {
    var vaultConfig = mock(VaultConfig.class);
    when(configurationProperties.getStoreType()).thenReturn(ModuleUserProviderType.VAULT);
    when(configurationProperties.getVault()).thenReturn(vaultConfig);
    when(vaultConfig.getToken()).thenReturn("vault-token");

    var actual = moduleUserConfiguration.secureStore();

    assertThat(actual).isNotNull();
  }
}
