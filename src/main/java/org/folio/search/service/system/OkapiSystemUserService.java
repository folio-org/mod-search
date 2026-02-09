package org.folio.search.service.system;

import lombok.RequiredArgsConstructor;
import org.folio.spring.service.PrepareSystemUserService;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

/**
 * This is a workaround for Okapi builds, for cases when mod-search will be enabled before mod-inventory-storage.
 * Assigning system user permission will fail in this case.
 * This class should be removed when system user logic will be removed.
 */
@Service
@RequiredArgsConstructor
public class OkapiSystemUserService {

  private final PrepareSystemUserService prepareSystemUserService;

  @Retryable(maxRetries = 10, delay = 2000, multiplier = 2)
  public void prepareSystemUser() {
    prepareSystemUserService.setupSystemUser();
  }
}
