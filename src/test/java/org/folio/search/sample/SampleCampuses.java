package org.folio.search.sample;

import static org.folio.search.utils.TestUtils.readJsonFromFile;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SampleCampuses {

  private static final List<Map<String, Object>> CAMPUSES_RECORD_AS_MAP =
    readJsonFromFile("/samples/campus-sample/campuses.json", new TypeReference<>() { });

  public static List<Map<String, Object>> getCampusesSampleAsMap() {
    return CAMPUSES_RECORD_AS_MAP;
  }
}
