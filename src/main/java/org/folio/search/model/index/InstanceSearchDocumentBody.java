package org.folio.search.model.index;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.types.IndexActionType;
import org.folio.search.model.types.IndexingDataFormat;
import org.opensearch.core.common.bytes.BytesReference;

/**
 * Document body for flat instance search index operations.
 * Carries target index, routing key, and source version for external versioning.
 */
@Data
@AllArgsConstructor(staticName = "of")
public class InstanceSearchDocumentBody {

  private BytesReference documentBody;
  private IndexingDataFormat dataFormat;
  private ResourceEvent resourceEvent;
  private IndexActionType action;
  private String targetIndex;
  private String routingKey;
  private long sourceVersion;

  public String getId() {
    return resourceEvent.getId();
  }

  public String getTenant() {
    return resourceEvent.getTenant();
  }
}
