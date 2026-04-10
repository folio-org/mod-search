package org.folio.search.model.reindex;

import java.util.UUID;
import lombok.Data;

@Data
public class StreamingReindexStatusEntity {

  public static final String ID_COLUMN = "id";
  public static final String FAMILY_ID_COLUMN = "family_id";
  public static final String JOB_ID_COLUMN = "job_id";
  public static final String RESOURCE_TYPE_COLUMN = "resource_type";
  public static final String STATUS_COLUMN = "status";

  private final UUID id;
  private final UUID familyId;
  private final UUID jobId;
  private final String resourceType;
  private String status;

  public StreamingReindexStatusEntity(UUID id, UUID familyId, UUID jobId, String resourceType, String status) {
    this.id = id;
    this.familyId = familyId;
    this.jobId = jobId;
    this.resourceType = resourceType;
    this.status = status;
  }
}
