package org.folio.search.model.index;

import java.util.Set;

public record CallNumberResource(
  String id,
  String fullCallNumber,
  String callNumber,
  String callNumberPrefix,
  String callNumberSuffix,
  String callNumberTypeId,
  String volume,
  String enumeration,
  String chronology,
  String copyNumber,
  Set<InstanceSubResource> instances) { }
