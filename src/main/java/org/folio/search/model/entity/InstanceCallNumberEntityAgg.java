package org.folio.search.model.entity;

import java.util.Set;
import org.folio.search.model.index.InstanceSubResource;

public record InstanceCallNumberEntityAgg(
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
