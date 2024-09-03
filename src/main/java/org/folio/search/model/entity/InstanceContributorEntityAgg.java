package org.folio.search.model.entity;

import java.util.Set;
import org.folio.search.model.index.InstanceSubResource;

public record InstanceContributorEntityAgg(
  String id,
  String name,
  String nameTypeId,
  String authorityId,
  Set<InstanceSubResource> instances
) { }
