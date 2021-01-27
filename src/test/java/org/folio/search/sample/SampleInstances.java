package org.folio.search.sample;

import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;

import java.io.InputStream;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

@UtilityClass
public class SampleInstances {
  private static final RawRecord SEMANTIC_WEB = readSampleInstance("semantic_web_primer.json");

  public static RawRecord getSemanticWeb() {
    return SEMANTIC_WEB;
  }

  @SneakyThrows
  private static RawRecord readSampleInstance(String fileName) {
    return OBJECT_MAPPER.readValue(getSampleAsStream(fileName), RawRecord.class);
  }

  private static InputStream getSampleAsStream(String fileName) {
    return SampleInstances.class.getResourceAsStream("/samples/" + fileName);
  }
}
