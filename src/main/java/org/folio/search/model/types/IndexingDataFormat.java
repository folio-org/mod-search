package org.folio.search.model.types;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.opensearch.common.xcontent.XContentType;

@Getter
@RequiredArgsConstructor
public enum IndexingDataFormat {
  JSON(XContentType.JSON),
  SMILE(XContentType.SMILE);

  private final XContentType xContentType;
}
