package org.folio.search.model.types;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * OpenSearch operation named in the error of a failed request.
 */
@Getter
@RequiredArgsConstructor
public enum SearchOperationType {

  ANALYZE("analyzeApi"),
  BULK("bulkApi"),
  CLEAR_SCROLL("clearScrollApi"),
  CREATE_INDEX("createIndexApi"),
  DELETE_BY_QUERY("deleteByQueryApi"),
  DROP_INDEX("dropIndex"),
  INDEX_EXISTS("indexExists"),
  MULTI_SEARCH("multiSearchApi"),
  PUT_INDEX_SETTINGS("putIndexSettingsApi"),
  PUT_MAPPINGS("putMappingsApi"),
  REFRESH("refreshApi"),
  SCROLL("scrollApi"),
  SEARCH("searchApi"),
  UPDATE_BY_QUERY("updateByQueryApi");

  private final String value;
}
