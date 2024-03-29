package org.folio.search.model.event;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.folio.spring.tools.kafka.BaseKafkaMessage;

@Getter
@Setter
@RequiredArgsConstructor
@ToString
@EqualsAndHashCode
public class ConsortiumInstanceEvent implements BaseKafkaMessage {

  private final String instanceId;

  private String tenant;
  private String ts;
}
