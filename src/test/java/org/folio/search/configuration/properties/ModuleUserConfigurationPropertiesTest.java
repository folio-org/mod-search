package org.folio.search.configuration.properties;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import org.folio.search.configuration.properties.ModuleUserConfigurationProperties.EphemeralConfig;
import org.folio.search.configuration.properties.ModuleUserConfigurationProperties.ModuleUserProviderType;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@UnitTest
class ModuleUserConfigurationPropertiesTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @ParameterizedTest
  @ValueSource(strings = {"str:str", "abc:abc:&isj", "asfgd-10", "tenantId:username:password,asd:asd"})
  void validate_negative_invalidEphemeralCredentials(String credentialsValue) {
    var configuration = new ModuleUserConfigurationProperties();
    configuration.setStoreType(ModuleUserProviderType.EPHEMERAL);
    configuration.setEphemeral(ephemeralConfig(credentialsValue.split(",")));

    var validationResult = validator.validate(configuration);

    assertThat(validationResult).isNotEmpty()
      .map(ConstraintViolation::getMessage)
      .containsExactly("must match \"([\\w\\-]+):([\\w\\-]+):([\\w\\-]+)\"");
  }

  @ParameterizedTest
  @ValueSource(strings = {"tenant-id:mod-search:qwerty", "tenant:search:qwerty"})
  void validate_positive_validEphemeralCredentialsValue(String credentialsValue) {
    var configuration = new ModuleUserConfigurationProperties();
    configuration.setStoreType(ModuleUserProviderType.EPHEMERAL);
    configuration.setEphemeral(ephemeralConfig(credentialsValue.split(",")));

    var validationResult = validator.validate(configuration);

    assertThat(validationResult).isEmpty();
  }

  @Test
  void validate_negative_positive() {
    var configuration = new ModuleUserConfigurationProperties();
    configuration.setStoreType(ModuleUserProviderType.AWS_SSM);

    var validationResult = validator.validate(configuration);

    assertThat(validationResult).isEmpty();
  }

  @Test
  void validate_negative_storeType() {
    var configuration = new ModuleUserConfigurationProperties();
    configuration.setStoreType(null);

    var validationResult = validator.validate(configuration);

    assertThat(validationResult).isNotEmpty()
      .map(ConstraintViolation::getMessage)
      .containsExactly("must not be null");
  }

  private static EphemeralConfig ephemeralConfig(String... values) {
    var config = new EphemeralConfig();
    config.setCredentials(List.of(values));
    return config;
  }
}
