package org.folio.search.sample;

import static org.folio.search.utils.TestUtils.readJsonFromFile;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;


@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SampleLocations {

  private static final List<Map<String, Object>> LOCATIONS_RECORD_AS_MAP =
    readJsonFromFile("/samples/location-sample/locations.json", new TypeReference<>() { });

  public static List<Map<String, Object>> getLocationsSampleAsMap() {
    return LOCATIONS_RECORD_AS_MAP;
  }
}
