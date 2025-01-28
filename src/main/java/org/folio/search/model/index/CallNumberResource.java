package org.folio.search.model.index;

import java.util.Set;

public record CallNumberResource(
  String id,
  String fullCallNumber,
  String callNumber,
  String callNumberPrefix,
  String callNumberSuffix,
  String callNumberTypeId,
  Set<InstanceSubResource> instances) { }
