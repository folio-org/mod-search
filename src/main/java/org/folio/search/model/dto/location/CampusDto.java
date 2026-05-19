package org.folio.search.model.dto.location;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

/**
 * Describes Campus object that comes from external channels.
 */
@Data
@SuperBuilder(toBuilder = true)
@Jacksonized
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CampusDto extends BaseLocationDto {

  @JsonProperty("institutionId")
  private String institutionId;
}
