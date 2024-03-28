package org.folio.search.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/**
 * Describes Location object that comes from external channels.
 */
@Data
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class LocationDto {

  @JsonProperty("id")
  private String id;
  @JsonProperty("name")
  private String name;
  @JsonProperty("code")
  private String code;
  @JsonProperty("description")
  private String description;
  @JsonProperty("discoveryDisplayName")
  private String discoveryDisplayName;
  @JsonProperty("isActive")
  private Boolean isActive;
  @JsonProperty("institutionId")
  private String institutionId;
  @JsonProperty("campusId")
  private String campusId;
  @JsonProperty("libraryId")
  private String libraryId;
  @JsonProperty("primaryServicePoint")
  private UUID primaryServicePoint;
  @JsonProperty("servicePointIds")
  private List<UUID> servicePointIds;

}
