package org.folio.search.repository.classification;

import java.util.Objects;
import lombok.Builder;

public record InstanceClassificationEntity(
  Id id,
  boolean shared
) {

  public InstanceClassificationEntity {
    Objects.requireNonNull(id);
  }

  public String type() {
    return id().type();
  }

  public String number() {
    return id().number();
  }

  public String instanceId() {
    return id().instanceId();
  }

  public String tenantId() {
    return id().tenantId();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    InstanceClassificationEntity that = (InstanceClassificationEntity) o;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Builder
  public record Id(String type,
                   String number,
                   String instanceId,
                   String tenantId) {
    public Id {
      Objects.requireNonNull(number);
      Objects.requireNonNull(instanceId);
      Objects.requireNonNull(tenantId);
    }
  }
}
