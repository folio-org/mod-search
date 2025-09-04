package org.folio.search.model.event;

import java.sql.Timestamp;
import java.util.UUID;
import lombok.Data;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.spring.tools.kafka.BaseKafkaMessage;

@Data
public class ReindexRangeIndexEvent implements BaseKafkaMessage {

  private UUID id;
  private ReindexEntityType entityType;
  private String lower;
  private String upper;

  private String tenant;
  private String ts;
  private String memberTenantId;
  
  private final Timestamp timestampFilter;
  private final boolean isTimestampBasedRange;
  
  // Default constructor for Kafka serialization
  public ReindexRangeIndexEvent() {
    this.timestampFilter = null;
    this.isTimestampBasedRange = false;
  }
  
  // Private constructor for factory methods
  private ReindexRangeIndexEvent(UUID id, ReindexEntityType entityType, String lower, String upper,
                                String memberTenantId, Timestamp timestampFilter, boolean isTimestampBasedRange) {
    this.id = id;
    this.entityType = entityType;
    this.lower = lower;
    this.upper = upper;
    this.memberTenantId = memberTenantId;
    this.timestampFilter = timestampFilter;
    this.isTimestampBasedRange = isTimestampBasedRange;
  }
  
  /**
   * Creates a standard reindex range event without timestamp filtering.
   */
  public static ReindexRangeIndexEvent createStandard(UUID id, ReindexEntityType entityType, 
                                                     String lower, String upper, String memberTenantId) {
    return new ReindexRangeIndexEvent(id, entityType, lower, upper, memberTenantId, null, false);
  }
  
  /**
   * Creates a timestamp-based reindex range event for member tenant child resource reindexing.
   */
  public static ReindexRangeIndexEvent createTimestampBased(UUID id, ReindexEntityType entityType,
                                                           String lower, String upper, String memberTenantId,
                                                           Timestamp timestampFilter) {
    return new ReindexRangeIndexEvent(id, entityType, lower, upper, memberTenantId, timestampFilter, true);
  }
}
