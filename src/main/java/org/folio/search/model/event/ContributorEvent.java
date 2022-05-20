package org.folio.search.model.event;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ContributorEvent {

  String id;
  String instanceId;
  String name;
  String nameTypeId;
  String typeId;
}
