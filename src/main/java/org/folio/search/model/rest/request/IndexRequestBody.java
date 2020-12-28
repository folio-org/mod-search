package org.folio.search.model.rest.request;

import javax.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class IndexRequestBody {

  /**
   * Name of resource.
   */
  @NotEmpty
  private String resourceName;
}
