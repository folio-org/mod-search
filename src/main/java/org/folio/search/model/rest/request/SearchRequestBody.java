package org.folio.search.model.rest.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class SearchRequestBody {

  /**
   * Resource name.
   */
  private String resource = "instance";

  /**
   * Search query.
   */
  private String query;

  /**
   * Request page number.
   */
  private Integer limit = 100;

  /**
   * Request page size.
   */
  private Integer offset = 0;
}
