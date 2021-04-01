package org.folio.search.exception;

import java.util.Arrays;

public class TenantNotInitializedException extends RuntimeException {
  public TenantNotInitializedException(String ... tenants) {
    // This will construct an exception without stack trace.
    super("Following tenants might not be initialized yet: " + Arrays.toString(tenants),
      null, false, false);
  }
}
