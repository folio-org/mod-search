package org.folio.search.model.entity;

import java.util.Set;
import org.folio.search.model.index.InstanceSubResource;

public record InstanceSubjectEntityAgg(
  String id,
  String value,
  String authorityId,
  Set<InstanceSubResource> instances
) { }