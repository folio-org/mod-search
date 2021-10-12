package org.folio.search.model.config;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@NoArgsConstructor
@Table(name = "feature_config")
@AllArgsConstructor(staticName = "of")
public class FeatureConfigEntity {

  /**
   * Feature name as {@link String} object.
   */
  @Id
  private String featureId;

  /**
   * Specifies if feature is enabled or not.
   */
  private boolean enabled;
}
