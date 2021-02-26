package org.folio.search.service.context;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.folio.search.model.SystemUser;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;

@RequiredArgsConstructor
public final class SystemUserFolioExecutionContext implements FolioExecutionContext {
  private final SystemUser systemUser;
  private final FolioModuleMetadata moduleMetadata;

  @Override
  public String getTenantId() {
    return systemUser.getTenantId();
  }

  @Override
  public String getOkapiUrl() {
    return systemUser.getOkapiUrl();
  }

  @Override
  public String getToken() {
    return systemUser.getOkapiToken();
  }

  @Override
  public String getUserName() {
    return systemUser.getUsername();
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
