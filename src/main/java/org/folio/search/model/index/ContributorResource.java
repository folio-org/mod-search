package org.folio.search.model.index;

import java.util.Set;

public record ContributorResource(
  String id,
  String name,
  String contributorNameTypeId,
  String authorityId,
  Set<InstanceSubResource> instances) {
}
