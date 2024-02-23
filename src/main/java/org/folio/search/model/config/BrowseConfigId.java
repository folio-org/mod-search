package org.folio.search.model.config;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.proxy.HibernateProxy;

@Getter
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
public class BrowseConfigId {

  @Column(name = "browse_type")
  private String browseType;
  @Column(name = "browse_option_type")
  private String browseOptionType;

  @Override
  public final int hashCode() {
    return Objects.hash(browseType, browseOptionType);
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null) {
      return false;
    }
    Class<?> effectiveClass = o instanceof HibernateProxy
                              ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass()
                              : o.getClass();
    Class<?> thisEffectiveClass = this instanceof HibernateProxy
                                  ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass()
                                  : this.getClass();
    if (thisEffectiveClass != effectiveClass) {
      return false;
    }
    BrowseConfigId that = (BrowseConfigId) o;
    return browseType != null && Objects.equals(browseType, that.browseType)
           && browseOptionType != null && Objects.equals(browseOptionType, that.browseOptionType);
  }
}
