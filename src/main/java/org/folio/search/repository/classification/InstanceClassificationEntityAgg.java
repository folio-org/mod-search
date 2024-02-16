package org.folio.search.repository.classification;

import java.util.Set;
import org.folio.search.model.index.InstanceSubResource;

public record InstanceClassificationEntityAgg(
  String typeId,
  String number,
  Set<InstanceSubResource> instances
) {

}
