package org.folio.search.repository.classification;

import java.util.List;
import org.folio.search.model.index.InstanceSubResource;

public record InstanceClassificationEntityAgg(
  String type,
  String number,
  List<InstanceSubResource> instances
) {

}
