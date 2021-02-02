package org.folio.search.sample;

import lombok.experimental.UtilityClass;
import org.folio.search.domain.dto.Instance;
import org.folio.search.utils.TestUtils;

@UtilityClass
public class SampleInstances {
  private static final Instance SEMANTIC_WEB = readSampleInstance("semantic_web_primer.json");

  public static Instance getSemanticWeb() {
    return SEMANTIC_WEB;
  }

  private static Instance readSampleInstance(String fileName) {
    return TestUtils.readJsonFromFile("/samples/" + fileName, Instance.class);
  }
}
