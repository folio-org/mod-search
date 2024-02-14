package org.folio.search.model.index;

import java.util.Set;

public record ClassificationResource(
  String id,
  String typeId,
  String number,
  Set<InstanceSubResource> instances) {
}
