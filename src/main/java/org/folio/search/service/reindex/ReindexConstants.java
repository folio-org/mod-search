package org.folio.search.service.reindex;

import static org.folio.search.model.types.ReindexEntityType.CLASSIFICATION;
import static org.folio.search.model.types.ReindexEntityType.CONTRIBUTOR;
import static org.folio.search.model.types.ReindexEntityType.HOLDING;
import static org.folio.search.model.types.ReindexEntityType.INSTANCE;
import static org.folio.search.model.types.ReindexEntityType.ITEM;
import static org.folio.search.model.types.ReindexEntityType.SUBJECT;
import static org.folio.search.utils.SearchUtils.CONTRIBUTOR_RESOURCE;
import static org.folio.search.utils.SearchUtils.INSTANCE_CLASSIFICATION_RESOURCE;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.SearchUtils.INSTANCE_SUBJECT_RESOURCE;

import java.util.Map;
import java.util.Set;
import org.folio.search.model.types.ReindexEntityType;

public final class ReindexConstants {

  public static final Map<ReindexEntityType, String> RESOURCE_NAME_MAP = Map.of(
    INSTANCE, INSTANCE_RESOURCE,
    SUBJECT, INSTANCE_SUBJECT_RESOURCE,
    CLASSIFICATION, INSTANCE_CLASSIFICATION_RESOURCE,
    CONTRIBUTOR, CONTRIBUTOR_RESOURCE
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

  private ReindexConstants() {}

}
