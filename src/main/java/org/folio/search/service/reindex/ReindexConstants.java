package org.folio.search.service.reindex;

import static org.folio.search.model.types.ReindexEntityType.HOLDINGS;
import static org.folio.search.model.types.ReindexEntityType.INSTANCE;
import static org.folio.search.model.types.ReindexEntityType.ITEM;

import java.util.Map;
import java.util.Set;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ResourceType;

public final class ReindexConstants {

  public static final Map<ReindexEntityType, ResourceType> RESOURCE_NAME_MAP = Map.of(
    ReindexEntityType.INSTANCE, ResourceType.INSTANCE,
    ReindexEntityType.SUBJECT, ResourceType.INSTANCE_SUBJECT,
    ReindexEntityType.CLASSIFICATION, ResourceType.INSTANCE_CLASSIFICATION,
    ReindexEntityType.CONTRIBUTOR, ResourceType.INSTANCE_CONTRIBUTOR
  );

  public static final String CLASSIFICATION_TABLE = "classification";
  public static final String CONTRIBUTOR_TABLE = "contributor";
  public static final String HOLDING_TABLE = "holding";
  public static final String INSTANCE_TABLE = "instance";
  public static final String INSTANCE_CLASSIFICATION_TABLE = "instance_classification";
  public static final String INSTANCE_CONTRIBUTOR_TABLE = "instance_contributor";
  public static final String INSTANCE_SUBJECT_TABLE = "instance_subject";
  public static final String ITEM_TABLE = "item";
  public static final String MERGE_RANGE_TABLE = "merge_range";
  public static final String SUBJECT_TABLE = "subject";
  public static final String UPLOAD_RANGE_TABLE = "upload_range";
  public static final String REINDEX_STATUS_TABLE = "reindex_status";

  public static final Set<ReindexEntityType> MERGE_RANGE_ENTITY_TYPES = Set.of(INSTANCE, ITEM, HOLDINGS);

  private ReindexConstants() { }

}
