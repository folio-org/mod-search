package org.folio.search.integration;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;

@RequiredArgsConstructor
final class AsyncFolioExecutionContext implements FolioExecutionContext {
  private final String tenantId;
  private final FolioModuleMetadata moduleMetadata;

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public String getOkapiUrl() {
    throw new UnsupportedOperationException("No okapiUrl available");
  }

  @Override
  public String getToken() {
    throw new UnsupportedOperationException("No token available");
  }

  @Override
  public String getUserName() {
    throw new UnsupportedOperationException("No userName available");
  }

  @Override
  public Map<String, Collection<String>> getAllHeaders() {
    return Collections.emptyMap();
  }

  @Override
  public Map<String, Collection<String>> getOkapiHeaders() {
    return Collections.emptyMap();
  }

  @Override
  public FolioModuleMetadata getFolioModuleMetadata() {
    return moduleMetadata;
  }
}
