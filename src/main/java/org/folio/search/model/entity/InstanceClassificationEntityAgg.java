package org.folio.search.model.entity;

import java.util.Set;
import org.folio.search.model.index.InstanceSubResource;

public record InstanceClassificationEntityAgg(
  String id,
  String typeId,
  String number,
  Set<InstanceSubResource> instances
) { }
