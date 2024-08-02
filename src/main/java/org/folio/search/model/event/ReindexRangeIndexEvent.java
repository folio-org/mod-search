package org.folio.search.model.event;

import java.util.UUID;
import lombok.Data;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.spring.tools.kafka.BaseKafkaMessage;

@Data
public class ReindexRangeIndexEvent implements BaseKafkaMessage {

  private UUID id;
  private ReindexEntityType entityType;
  private int offset;
  private int limit;

  private String tenant;
  private String ts;
}
