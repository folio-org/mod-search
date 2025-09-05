package org.folio.search.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.model.types.ResourceType;

public interface ResourceRequest {

  /**
   * Returns tenant id as {@link String} object.
   *
   * @return tenant id
   */
  String tenantId();

  /**
   * Returns resource name as {@link String} object.
   *
   * @return resource name
   */
  ResourceType resource();

  static List<String> parseIncludeField(String include) {
    if (StringUtils.isNotBlank(include)) {
      return Arrays.asList(StringUtils.split(StringUtils.deleteWhitespace(include), ','));
    }
    return Collections.emptyList();
  }
}
