package org.folio.search.service.reindex;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class StagedCutoverSnapshotDetailsHelper {

  private StagedCutoverSnapshotDetailsHelper() {
  }

  public static Map<String, Object> createBaseDetails(ReindexKafkaConsumerManager consumerManager, UUID familyId) {
    var details = new LinkedHashMap<String, Object>();
    consumerManager.getStagedCutoverSnapshotCapturedAt(familyId)
      .map(Instant::toString)
      .ifPresent(value -> details.put("snapshotCapturedAt", value));
    var targetPartitions = consumerManager.getStagedCutoverSnapshotPartitionCount(familyId);
    details.put("targetPartitions", targetPartitions);
    details.put("partitions", targetPartitions);
    details.put("lagMeasuredAgainst", "stagedSnapshot");
    return details;
  }

  public static void applyLagDetails(Map<String, Object> details, Long consumerLag, Long lagToTarget) {
    if (consumerLag != null) {
      details.put("consumerLag", consumerLag);
    }
    if (lagToTarget != null) {
      details.put("consumerLagToTarget", lagToTarget);
      details.put("readyForSwitchOver", lagToTarget == 0L);
    }
  }
}
