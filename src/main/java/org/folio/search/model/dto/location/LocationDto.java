package org.folio.search.model.dto.location;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

/**
 * Describes Location object that comes from external channels.
 */
@Data
@SuperBuilder(toBuilder = true)
@Jacksonized
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class LocationDto extends BaseLocationDto {

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
