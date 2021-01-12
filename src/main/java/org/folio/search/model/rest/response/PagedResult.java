package org.folio.search.model.rest.response;

import java.util.List;

public interface PagedResult<T> {

  long getTotalRecords();

  List<T> getInstances();
}
