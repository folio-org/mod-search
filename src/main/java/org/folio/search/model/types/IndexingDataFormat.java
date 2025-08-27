package org.folio.search.model.types;

import lombok.Getter;
import org.opensearch.common.xcontent.XContentType;

@Getter
public enum IndexingDataFormat {
  JSON(XContentType.JSON),
  SMILE(XContentType.SMILE);

  private final XContentType xcontentType;

  IndexingDataFormat(XContentType xcontentType) {
    this.xcontentType = xcontentType;
  }
}
