package org.folio.search.service.converter;

import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.folio.search.model.metadata.ResourceDescription;

/**
 * The conversion context object.
 */
@Data
@Getter
@RequiredArgsConstructor(staticName = "of")
public class ConversionContext {

  /**
   * Resource id.
   */
  private final String id;

  /**
   * Resource tenant id.
   */
  private final String tenant;

  /**
   * Resource fields as map.
   */
  private final Map<String, Object> resourceData;

  /**
   * Resource description for conversion.
   */
  private final ResourceDescription resourceDescription;

  /**
   * List of supported language for resource.
   */
  private final List<String> languages;
}
