package org.folio.support.base;

import static org.folio.support.base.BaseIntegrationTest.lockSubResourceWithRetry;

import java.sql.Timestamp;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.service.reindex.jdbc.SubResourcesLockRepository;
import org.folio.support.testdata.SharedTestDataManager.LockManager;

/**
 * Holds the sub-resource locks of a tenant while shared test data is loaded, so that the scheduled
 * sub-resource processing does not race with the data load.
 */
public final class SubResourceLockManager implements LockManager {

  private final SubResourcesLockRepository lockRepository;
  private final String tenantId;
  private final List<ReindexEntityType> entityTypes;
  private final Map<ReindexEntityType, Timestamp> locks = new EnumMap<>(ReindexEntityType.class);

  public SubResourceLockManager(SubResourcesLockRepository lockRepository, String tenantId,
                                ReindexEntityType... entityTypes) {
    this.lockRepository = lockRepository;
    this.tenantId = tenantId;
    this.entityTypes = List.of(entityTypes);
  }

  /**
   * Locks all entity types; if one of them cannot be locked, the locks taken so far are released.
   */
  @Override
  public void lockAll() {
    try {
      entityTypes.forEach(type -> locks.put(type, lockSubResourceWithRetry(lockRepository, type, tenantId)));
    } catch (RuntimeException e) {
      unlockAll();
      throw e;
    }
  }

  @Override
  public void unlockAll() {
    locks.forEach((type, lock) -> lockRepository.unlockSubResourceFenced(type, lock, tenantId, lock));
    locks.clear();
  }
}
