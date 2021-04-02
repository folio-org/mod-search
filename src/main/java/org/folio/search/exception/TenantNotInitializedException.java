package org.folio.search.exception;

import java.util.Arrays;

public class TenantNotInitializedException extends RuntimeException {
  public TenantNotInitializedException(String[] tenants, Throwable cause) {
    // This will construct an exception without stack trace.
    super("Following tenants might not be initialized yet: " + Arrays.toString(tenants),
      cause, false, false);
  }
}
