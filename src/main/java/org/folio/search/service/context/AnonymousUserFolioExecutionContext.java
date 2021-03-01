package org.folio.search.service.context;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;

/**
 * FolioExecutionContext for an anonymous call to okapi. Usually used for
 * an unsecured API.
 */
@RequiredArgsConstructor
public final class AnonymousUserFolioExecutionContext implements FolioExecutionContext {
  private final String tenantId;
  private final String okapiUrl;

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
    return null;
  }

  @Override
  public String getUserName() {
    return null;
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
    throw new UnsupportedOperationException("getFolioModuleMetadata");
  }
}
