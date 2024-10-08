package org.folio.search.model.reindex;

import java.sql.Timestamp;
import java.util.UUID;
import lombok.Data;
import org.folio.search.model.types.ReindexEntityType;

@Data
public class UploadRangeEntity {

  public static final String ID_COLUMN = "id";
  public static final String ENTITY_TYPE_COLUMN = "entity_type";
  public static final String LOWER_BOUND_COLUMN = "lower";
  public static final String UPPER_BOUND_COLUMN = "upper";
  public static final String CREATED_AT_COLUMN = "created_at";
  public static final String FINISHED_AT_COLUMN = "finished_at";

  private final UUID id;
  private final ReindexEntityType entityType;
  private final String lower;
  private final String upper;
  private final Timestamp createdAt;
  private Timestamp finishedAt;

}
