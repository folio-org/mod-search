package org.folio.search.service.indexrebuild;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.experimental.UtilityClass;

/**
 * Range ids of an index rebuild are name-based (version 3) UUIDs. Inventory echoes the range id back with the records,
 * and the regular reindex uses random (version 4) UUIDs, so the id alone tells who requested the records. This needs no
 * lookup, and records of an abandoned run are still recognized after its ranges were removed.
 */
@UtilityClass
public class IndexRebuildRangeIds {

  private static final int NAME_BASED_VERSION = 3;

  public static UUID newId() {
    return UUID.nameUUIDFromBytes(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
  }

  public static boolean isRebuildRange(String rangeId) {
    if (rangeId == null) {
      return false;
    }
    try {
      return UUID.fromString(rangeId).version() == NAME_BASED_VERSION;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
