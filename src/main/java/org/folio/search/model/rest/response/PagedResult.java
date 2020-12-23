package org.folio.search.model.rest.response;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PagedResult<T> {

  private long total;
  private long totalPages;

  private int pageSize;
  private int pageNumber;

  private List<T> content;
}
