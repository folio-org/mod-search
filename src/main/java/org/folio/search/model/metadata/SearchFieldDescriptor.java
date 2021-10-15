package org.folio.search.model.metadata;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.folio.search.domain.dto.TenantConfiguredFeature;

@Data
@EqualsAndHashCode(callSuper = true)
public class SearchFieldDescriptor extends PlainFieldDescription {

  /**
   * Name of a Spring bean that is used to generate value for the field.
   *
   * @see org.folio.search.service.setter.FieldProcessor
   */
  private String processor;

  /**
   * Marks if field processor can accept raw resource as {@link java.util.Map} or not.
   */
  private boolean rawProcessing;

  /**
   * Provides feature name, that should be checked before indexing search field.
   */
  private TenantConfiguredFeature dependsOnFeature;
}
