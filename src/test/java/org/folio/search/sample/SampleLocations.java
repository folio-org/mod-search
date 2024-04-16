package org.folio.search.sample;

import static org.folio.search.utils.JsonConverter.MAP_TYPE_REFERENCE;
import static org.folio.search.utils.TestUtils.readJsonFromFile;

import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;


@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SampleLocations {

  private static final Map<String, Object> LOCATIONS_RECORD_AS_MAP =
    readJsonFromFile("/samples/location-sample/locations.json", MAP_TYPE_REFERENCE);

  public static Map<String, Object> getLocationsSampleAsMap() {
    return LOCATIONS_RECORD_AS_MAP;
  }
}
