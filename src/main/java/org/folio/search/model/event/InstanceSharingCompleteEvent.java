package org.folio.search.model.event;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.UUID;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Data
public class InstanceSharingCompleteEvent {
  private UUID id;
  private String instanceIdentifier;
  private String sourceTenantId;
  private String targetTenantId;
  private Status status;
  private String error;

  @Getter
  @RequiredArgsConstructor
  public enum Status {

    IN_PROGRESS("IN_PROGRESS"),
    COMPLETE("COMPLETE"),
    ERROR("ERROR");

    @JsonValue
    private final String value;
  }
}
