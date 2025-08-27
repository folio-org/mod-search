package org.folio.search.service.reindex;

import org.springframework.stereotype.Component;

/**
 * Context holder to track when reindex operations are active.
 * Uses ThreadLocal to ensure thread-safe access during concurrent reindex operations.
 */
@Component
public class ReindexContext {

  private static final ThreadLocal<Boolean> REINDEX_MODE = ThreadLocal.withInitial(() -> false);

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
   * Clear the reindex context for the current thread.
   * Should be called in finally blocks to prevent memory leaks.
   */
  public static void clear() {
    REINDEX_MODE.remove();
  }
}
