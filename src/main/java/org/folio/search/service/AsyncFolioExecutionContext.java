package org.folio.search.service;

import static java.util.Collections.emptyMap;

import java.util.Collection;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;

@RequiredArgsConstructor
public final class AsyncFolioExecutionContext implements FolioExecutionContext {
  private final String tenantId;
  private final FolioModuleMetadata moduleMetadata;

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public String getOkapiUrl() {
    throw new UnsupportedOperationException("Okapi url is not supported");
  }

  @Override
  public String getToken() {
    throw new UnsupportedOperationException("Token is not supported");
  }

  @Override
  public String getUserName() {
    throw new UnsupportedOperationException("Username is not supported");
  }

  @Override
  public Map<String, Collection<String>> getAllHeaders() {
    return emptyMap();
  }

  @Override
  public Map<String, Collection<String>> getOkapiHeaders() {
    return emptyMap();
  }

  @Override
  public FolioModuleMetadata getFolioModuleMetadata() {
    return moduleMetadata;
  }
}
