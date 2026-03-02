package org.folio.search.model.event;

import lombok.Data;

@Data
public class ReindexFileReadyEvent {

  private final String tenantId;
  private final ReindexRecordType recordType;
  private final String rangeId;
  private final String traceId;
  private final String bucket;
  private final String objectKey;
  private final String createdDate;
}
