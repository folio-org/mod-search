package org.folio.search.service.context;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.With;
import org.folio.search.model.SystemUser;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FolioExecutionContextBuilder {
  private final FolioModuleMetadata moduleMetadata;

  public FolioExecutionContextBuilder.Builder builder() {
    return new Builder(moduleMetadata);
  }

  public FolioExecutionContext forSystemUser(SystemUser systemUser) {
    return builder()
      .withTenantId(systemUser.getTenantId())
      .withOkapiUrl(systemUser.getOkapiUrl())
      .withUsername(systemUser.getUsername())
      .withToken(systemUser.getToken())
      .build();
  }

  public FolioExecutionContext dbOnlyContext(String tenantId) {
    return builder().withTenantId(tenantId).build();
  }

  @With
  @AllArgsConstructor(access = AccessLevel.PRIVATE)
  public static class Builder {
    private final FolioModuleMetadata moduleMetadata;
    private String tenantId;
    private String okapiUrl;
    private String token;
    private String username;
    private final Map<String, Collection<String>> allHeaders;
    private final Map<String, Collection<String>> okapiHeaders;

    public Builder(FolioModuleMetadata moduleMetadata) {
      this.moduleMetadata = moduleMetadata;
      this.allHeaders = new HashMap<>();
      this.okapiHeaders = new HashMap<>();
    }

    public FolioExecutionContext build() {
      return new FolioExecutionContext() {
        @Override
        public String getTenantId() {
          return tenantId;
        }

        @Override
        public String getOkapiUrl() {
          return okapiUrl;
        }

        @Override
        public String getToken() {
          return token;
        }

        @Override
        public String getUserName() {
          return username;
        }

        @Override
        public Map<String, Collection<String>> getAllHeaders() {
          return allHeaders;
        }

        @Override
        public Map<String, Collection<String>> getOkapiHeaders() {
          return okapiHeaders;
        }

        @Override
        public FolioModuleMetadata getFolioModuleMetadata() {
          return moduleMetadata;
        }
      };
    }
  }
}
