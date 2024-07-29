package org.folio.search.model.reindex;

import java.sql.Timestamp;
import lombok.Data;
import org.folio.search.model.types.ReindexEntityType;

@Data
public class UploadRangeEntity {

  public static final String UPLOAD_RANGE_TABLE = "upload_range";
  public static final String ENTITY_TYPE_COLUMN = "entity_type";
  public static final String RANGE_LIMIT_COLUMN = "range_limit";
  public static final String RANGE_OFFSET_COLUMN = "range_offset";
  public static final String CREATED_AT_COLUMN = "created_at";
  public static final String FINISHED_AT_COLUMN = "finished_at";

  private final ReindexEntityType entityType;
  private final int limit;
  private final int offset;
  private final Timestamp createdAt;
  private Timestamp finishedAt;

}
