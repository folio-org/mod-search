package org.folio.support.sample;

import static org.folio.support.utils.JsonTestUtils.readJsonFromFile;

import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import tools.jackson.core.type.TypeReference;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SampleLocations {

  private static final List<Map<String, Object>> LOCATIONS_RECORD_AS_MAP =
    readJsonFromFile("/samples/location-sample/locations.json", new TypeReference<>() { });

  public static List<Map<String, Object>> getLocationsSampleAsMap() {
    return LOCATIONS_RECORD_AS_MAP;
  }
}
