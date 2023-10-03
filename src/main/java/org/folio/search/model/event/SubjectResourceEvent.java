package org.folio.search.model.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SubjectResourceEvent {

  String id;
  String value;
  String authorityId;
  String instanceId;
  boolean shared;
}
