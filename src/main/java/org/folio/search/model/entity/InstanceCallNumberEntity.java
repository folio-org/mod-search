package org.folio.search.model.entity;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@Builder
@EqualsAndHashCode
public class InstanceCallNumberEntity {

  private String callNumberId;
  private String itemId;
  private String instanceId;
  private String locationId;
  private String tenantId;

}
