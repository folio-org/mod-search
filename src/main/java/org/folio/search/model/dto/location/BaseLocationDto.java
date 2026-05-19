package org.folio.search.model.dto.location;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.folio.search.domain.dto.Metadata;

@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class BaseLocationDto {

  @JsonProperty("id")
  private String id;
  @JsonProperty("name")
  private String name;
  @JsonProperty("code")
  private String code;
  @JsonProperty("metadata")
  private Metadata metadata;
}
