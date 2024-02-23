package org.folio.search.model.config;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.folio.search.configuration.jpa.StringListConverter;
import org.hibernate.Hibernate;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Entity
@Table(name = "browse_config")
public class BrowseConfigEntity {

  @EmbeddedId
  private BrowseConfigId configId;

  @Column(name = "shelving_algorithm", nullable = false)
  private String shelvingAlgorithm;

  @Convert(converter = StringListConverter.class)
  @Column(name = "type_ids")
  private List<String> typeIds = new ArrayList<>();

  @Override
  public final int hashCode() {
    return Objects.hash(configId);
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null) {
      return false;
    }
    Class<?> effectiveClass = Hibernate.getClass(o);
    Class<?> thisEffectiveClass = Hibernate.getClass(this);
    if (thisEffectiveClass != effectiveClass) {
      return false;
    }
    BrowseConfigEntity that = (BrowseConfigEntity) o;
    return getConfigId() != null && Objects.equals(getConfigId(), that.getConfigId());
  }
}
