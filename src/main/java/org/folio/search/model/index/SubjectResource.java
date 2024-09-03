package org.folio.search.model.index;

import java.util.Set;

public record SubjectResource(
  String id,
  String value,
  String authorityId,
  Set<InstanceSubResource> instances) { }
