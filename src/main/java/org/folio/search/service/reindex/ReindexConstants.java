package org.folio.search.service.reindex;

import static org.folio.search.model.types.ReindexEntityType.HOLDING;
import static org.folio.search.model.types.ReindexEntityType.INSTANCE;
import static org.folio.search.model.types.ReindexEntityType.ITEM;

import java.util.Map;
import java.util.Set;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ResourceType;

public final class ReindexConstants {

  public static final Map<ReindexEntityType, String> RESOURCE_NAME_MAP = Map.of(
    ReindexEntityType.INSTANCE, ResourceType.INSTANCE.getName(),
    ReindexEntityType.SUBJECT, ResourceType.INSTANCE_SUBJECT.getName(),
    ReindexEntityType.CLASSIFICATION, ResourceType.INSTANCE_CLASSIFICATION.getName(),
    ReindexEntityType.CONTRIBUTOR, ResourceType.INSTANCE_CONTRIBUTOR.getName()
  );

  public static final String CLASSIFICATION_TABLE = "classification";
  public static final String CONTRIBUTOR_TABLE = "contributor";
  public static final String HOLDING_TABLE = "holding";
  public static final String INSTANCE_TABLE = "instance";
  public static final String ITEM_TABLE = "item";
  public static final String MERGE_RANGE_TABLE = "merge_range";
  public static final String SUBJECT_TABLE = "subject";
  public static final String UPLOAD_RANGE_TABLE = "upload_range";
  public static final String REINDEX_STATUS_TABLE = "reindex_status";

  public static final Set<ReindexEntityType> MERGE_RANGE_ENTITY_TYPES = Set.of(INSTANCE, ITEM, HOLDING);

  private ReindexConstants() { }

}
