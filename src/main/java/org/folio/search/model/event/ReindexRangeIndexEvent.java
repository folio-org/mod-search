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

//{
//  "id": "8b00efbd-07e0-48c1-a691-d259d9c67e37",
//  "entityType": "SUBJECT",
//  "offset": 0,
//  "limit": 10,
//  "tenant": "test_tenant"
//}

