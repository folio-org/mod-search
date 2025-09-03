package org.folio.search.service.reindex;

import org.springframework.stereotype.Component;

/**
 * Context holder to track when reindex operations are active.
 * Uses ThreadLocal to ensure thread-safe access during concurrent reindex operations.
 * Also tracks member tenant context for consortium-specific reindex operations.
 */
@Component
public class ReindexContext {

  private static final ThreadLocal<Boolean> REINDEX_MODE = ThreadLocal.withInitial(() -> false);
  private static final ThreadLocal<String> MEMBER_TENANT_ID = new ThreadLocal<>();

  /**
   * Set whether the current thread is in reindex mode.
   *
   * @param mode true if reindex mode is active, false otherwise
   */
  public static void setReindexMode(boolean mode) {
    REINDEX_MODE.set(mode);
  }

  /**
   * Check if the current thread is in reindex mode.
   *
   * @return true if reindex mode is active, false otherwise
   */
  public static boolean isReindexMode() {
    return REINDEX_MODE.get();
  }

  /**
   * Set the member tenant ID for consortium member tenant reindex operations.
   *
   * @param tenantId the member tenant ID
   */
  public static void setMemberTenantId(String tenantId) {
    MEMBER_TENANT_ID.set(tenantId);
  }

  /**
   * Get the current member tenant ID if set.
   *
   * @return the member tenant ID or null if not set
   */
  public static String getMemberTenantId() {
    return MEMBER_TENANT_ID.get();
  }

  /**
   * Check if the current operation is a member tenant reindex.
   *
   * @return true if member tenant ID is set, false otherwise
   */
  public static boolean isMemberTenantReindex() {
    return MEMBER_TENANT_ID.get() != null;
  }

  /**
   * Clear the member tenant ID for the current thread.
   * Should be called in finally blocks to prevent memory leaks.
   */
  public static void clearMemberTenantId() {
    MEMBER_TENANT_ID.remove();
  }

  /**
   * Clear all reindex context for the current thread.
   * Should be called in finally blocks to prevent memory leaks.
   */
  public static void clear() {
    REINDEX_MODE.remove();
    MEMBER_TENANT_ID.remove();
  }
}
