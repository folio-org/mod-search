package org.folio.search.model.dto.locationunit;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.With;
import lombok.extern.jackson.Jacksonized;

/**
 * Describes Institution object that comes from external channels.
 */
@Data
@With
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class InstitutionDto {

  @JsonProperty("id")
  private String id;
  @JsonProperty("name")
  private String name;
  @JsonProperty("code")
  private String code;

}
