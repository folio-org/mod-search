package org.folio.search.model.event;

import java.util.UUID;

public record ClassificationChunkEvent(
  String tenantId,
  UUID id,
  int offset,
  int limit
) {
}
