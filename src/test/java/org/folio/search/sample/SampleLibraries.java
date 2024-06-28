package org.folio.search.sample;

import static org.folio.search.utils.TestUtils.readJsonFromFile;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SampleLibraries {

  private static final List<Map<String, Object>> LIBRARIES_RECORD_AS_MAP =
    readJsonFromFile("/samples/library-sample/libraries.json", new TypeReference<>() { });

  public static List<Map<String, Object>> getLibrariesSampleAsMap() {
    return LIBRARIES_RECORD_AS_MAP;
  }
}
