package org.folio.search.model.event;

import java.util.List;
import lombok.Data;

@Data
public class ReindexRecordsEvent {

  private List<Object> records;
  private ReindexRecordType recordType;
  private String tenant;
  private String rangeId;
}
