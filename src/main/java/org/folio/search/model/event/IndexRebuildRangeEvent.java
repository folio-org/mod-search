package org.folio.search.model.event;

import java.util.UUID;
import lombok.Data;
import org.folio.spring.tools.kafka.BaseKafkaMessage;

/**
 * Asks to build documents of a range of a derived index. Details of the range are stored with the range.
 */
@Data
public class IndexRebuildRangeEvent implements BaseKafkaMessage {

  private UUID id;
  private String resource;
  private String tenant;
  private String ts;
}
