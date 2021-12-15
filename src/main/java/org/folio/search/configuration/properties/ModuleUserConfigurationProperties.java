package org.folio.search.configuration.properties;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.folio.edge.api.utils.security.AwsParamStore;
import org.folio.edge.api.utils.security.EphemeralStore;
import org.folio.edge.api.utils.security.VaultStore;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Component
@ConfigurationProperties("application.module-user")
public class ModuleUserConfigurationProperties {

  /**
   * Module user store type.
   */
  @NotNull
  private ModuleUserConfigurationProperties.ModuleUserProviderType storeType = ModuleUserProviderType.EPHEMERAL;

  /**
   * Cache capacity for token cache.
   */
  private int cacheCapacity = 100;

  /**
   * Cache ttl in ms.
   */
  private long cacheTtlMs = 3600000;

  /**
   * Failure cache ttl in ms.
   */
  private long failureCacheTtlMs = 30000;

  /**
   * Ephemeral configuration properties.
   */
  @Valid
  private EphemeralConfig ephemeral;

  /**
   * Aws Ssm configuration properties.
   */
  @Valid
  private AwsConfig awsSsm;

  /**
   * Vault configuration properties.
   */
  @Valid
  private VaultConfig vault;

  @Data
  public static class AwsConfig {

    /**
     * The AWS region to pass to the AWS SSM Client Builder.
     *
     * <p> If not set, the AWS Default Region Provider Chain is used to determine which region to use. </p>
     */
    private String region;

    /**
     * If true, will rely on the current IAM role for authorization instead of explicitly providing AWS credentials
     * (access_key/secret_key).
     */
    private Boolean useIam;

    /**
     * The HTTP endpoint to use for retrieving AWS credentials.
     */
    private String ecsCredentialsEndpoint;

    /**
     * The path component of the credentials' endpoint URI.
     *
     * <p> This value is appended to the credentials' endpoint to form the URI from which credentials can be obtained.
     * </p>
     *
     * <p> If omitted, the value will be read from the AWS_CONTAINER_CREDENTIALS_RELATIVE_URI environment variable
     * (standard on ECS containers) </p>
     *
     * <p>You won't typically need to set this unless using AwsParamStore from outside an ECS container</p>
     */
    private String ecsCredentialsPath;
  }

  @Data
  public static class VaultConfig {

    /**
     * Token for accessing vault, may be a root token.
     */
    private String token;

    /**
     * The address of your vault.
     */
    private String address;

    /**
     * Whether to use SSL.
     */
    private Boolean enableSsl;

    /**
     * The path to an X.509 certificate in unencrypted PEM format, using UTF-8 encoding.
     */
    private String pemFilePath;

    /**
     * The password used to access the JKS keystore (optional).
     */
    private String keystorePassword;

    /**
     * The path to a JKS keystore file containing a client cert and private key.
     */
    private String keystoreFilePath;

    /**
     * The path to a JKS truststore file containing Vault server certs that can be trusted.
     */
    private String truststoreFilePath;
  }

  @Data
  public static class EphemeralConfig {

    /**
     * Tenant credentials as string with following format: {@code tenantId:username:password}.
     */
    private List<@Pattern(regexp = "([\\w\\-]+):([\\w\\-]+):([\\w\\-]+)") String> credentials;

    @Data
    public static class UserCredentials {

      /**
       * Ephemeral module user username.
       */
      private String username;

      /**
       * Ephemeral module user password.
       */
      private String password;

      @Override
      public String toString() {
        return username + ',' + password;
      }
    }
  }

  @Getter
  @RequiredArgsConstructor
  public enum ModuleUserProviderType {

    /**
     * Used to extract credentials from AWS SSM service.
     */
    AWS_SSM(AwsParamStore.TYPE),

    /**
     * Properties with that type can be used only for development purposes.
     */
    EPHEMERAL(EphemeralStore.TYPE),

    /**
     * Used to extract credentials from Vault.
     */
    VAULT(VaultStore.TYPE);

    @JsonValue
    private final String value;

    @Override
    public String toString() {
      return value;
    }
  }
}
