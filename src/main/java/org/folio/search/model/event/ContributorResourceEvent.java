package org.folio.search.model.event;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ContributorResourceEvent {

  String id;
  String instanceId;
  String name;
  String nameTypeId;
  String typeId;
  String authorityId;
}
