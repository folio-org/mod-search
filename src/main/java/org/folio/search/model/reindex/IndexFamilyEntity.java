package org.folio.search.model.reindex;

import java.sql.Timestamp;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.folio.search.model.types.IndexFamilyStatus;
import org.folio.search.model.types.QueryVersion;

@Data
@AllArgsConstructor
public class IndexFamilyEntity {

  public static final String ID_COLUMN = "id";
  public static final String TENANT_ID_COLUMN = "tenant_id";
  public static final String GENERATION_COLUMN = "generation";
  public static final String INDEX_NAME_COLUMN = "index_name";
  public static final String STATUS_COLUMN = "status";
  public static final String CREATED_AT_COLUMN = "created_at";
  public static final String ACTIVATED_AT_COLUMN = "activated_at";
  public static final String RETIRED_AT_COLUMN = "retired_at";
  public static final String QUERY_VERSION_COLUMN = "query_version";

  private final UUID id;
  private final String tenantId;
  private final int generation;
  private final String indexName;
  private IndexFamilyStatus status;
  private final Timestamp createdAt;
  private Timestamp activatedAt;
  private Timestamp retiredAt;
  private final QueryVersion queryVersion;
}
