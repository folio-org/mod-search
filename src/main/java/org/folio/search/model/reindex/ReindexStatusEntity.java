package org.folio.search.model.reindex;

import java.sql.Timestamp;
import lombok.Data;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ReindexStatus;

@Data
public class ReindexStatusEntity {

  public static final String ENTITY_TYPE_COLUMN = "entity_type";
  public static final String STATUS_COLUMN = "status";
  public static final String TOTAL_MERGE_RANGES_COLUMN = "total_merge_ranges";
  public static final String PROCESSED_MERGE_RANGES_COLUMN = "processed_merge_ranges";
  public static final String TOTAL_UPLOAD_RANGES_COLUMN = "total_upload_ranges";
  public static final String PROCESSED_UPLOAD_RANGES_COLUMN = "processed_upload_ranges";
  public static final String START_TIME_MERGE_COLUMN = "start_time_merge";
  public static final String END_TIME_MERGE_COLUMN = "end_time_merge";
  public static final String START_TIME_UPLOAD_COLUMN = "start_time_upload";
  public static final String END_TIME_UPLOAD_COLUMN = "end_time_upload";

  private final ReindexEntityType entityType;
  private final ReindexStatus status;
  private int totalMergeRanges;
  private int processedMergeRanges;
  private int totalUploadRanges;
  private int processedUploadRanges;
  private Timestamp startTimeMerge;
  private Timestamp endTimeMerge;
  private Timestamp startTimeUpload;
  private Timestamp endTimeUpload;
}
