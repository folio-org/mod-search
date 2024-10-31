package org.folio.search.model.event;

import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.spring.tools.kafka.BaseKafkaMessage;

public class SubResourceEvent extends ResourceEvent implements BaseKafkaMessage {

  @Getter
  @Setter
  private String ts;

  @Override
  public boolean equals(Object o) {
    return super.equals(o) && Objects.equals(((SubResourceEvent) o).ts, this.ts);
  }

  @Override
  public int hashCode() {
    return super.hashCode() + Objects.hashCode(ts);
  }

  public static SubResourceEvent fromResourceEvent(ResourceEvent event) {
    return (SubResourceEvent) new SubResourceEvent()
      .id(event.getId())
      .type(event.getType())
      .deleteEventSubType(event.getDeleteEventSubType())
      .tenant(event.getTenant())
      .resourceName(event.getResourceName())
      ._new(event.getNew())
      .old(event.getOld());
  }
}
