package org.folio.search.model.reindex;

import java.sql.Timestamp;
import java.util.UUID;
import lombok.Data;

@Data
public class StreamingReindexStatusEntity {

  public static final String ID_COLUMN = "id";
  public static final String TENANT_ID_COLUMN = "tenant_id";
  public static final String FAMILY_ID_COLUMN = "family_id";
  public static final String RESOURCE_TYPE_COLUMN = "resource_type";
  public static final String STATUS_COLUMN = "status";
  public static final String RECORDS_PROCESSED_COLUMN = "records_processed";
  public static final String STARTED_AT_COLUMN = "started_at";
  public static final String COMPLETED_AT_COLUMN = "completed_at";
  public static final String ERROR_MESSAGE_COLUMN = "error_message";
  public static final String FAILED_BATCHES_COLUMN = "failed_batches";

  private final UUID id;
  private final String tenantId;
  private final UUID familyId;
  private final String resourceType;
  private String status;
  private long recordsProcessed;
  private long failedBatches;
  private final Timestamp startedAt;
  private Timestamp completedAt;
  private String errorMessage;
  private UUID jobId;

  public StreamingReindexStatusEntity(UUID id, String tenantId, UUID familyId, String resourceType,
                                      String status, long recordsProcessed, Timestamp startedAt,
                                      Timestamp completedAt, String errorMessage) {
    this.id = id;
    this.tenantId = tenantId;
    this.familyId = familyId;
    this.resourceType = resourceType;
    this.status = status;
    this.recordsProcessed = recordsProcessed;
    this.startedAt = startedAt;
    this.completedAt = completedAt;
    this.errorMessage = errorMessage;
  }
}
