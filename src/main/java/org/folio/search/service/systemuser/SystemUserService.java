package org.folio.search.service.systemuser;

import org.folio.search.model.SystemUser;
import org.folio.spring.FolioExecutionContext;

public interface SystemUserService {
  void prepareSystemUser(FolioExecutionContext context);

  SystemUser getSystemUser(String tenantId);
}
