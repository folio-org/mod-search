package org.folio.search.model.converter;

import java.util.List;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.metadata.ResourceDescription;

/**
 * The conversion context object.
 */
@Data
@Getter
@RequiredArgsConstructor(staticName = "of")
public class ConversionContext {

  /**
   * Resource event object.
   */
  private final ResourceEvent resourceEvent;

  /**
   * Resource description for conversion.
   */
  private final ResourceDescription resourceDescription;

  /**
   * List of supported language for resource.
   */
  private final List<String> languages;
}
