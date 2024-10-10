package org.folio.search.sample;

import static org.folio.search.utils.JsonConverter.MAP_TYPE_REFERENCE;
import static org.folio.search.utils.TestUtils.readJsonFromFile;

import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SampleLinkedData {

  public static Map<String, Object> getInstanceSampleAsMap() {
    return readJson("/samples/linked-data/instance.json");
  }

  public static Map<String, Object> getInstance2SampleAsMap() {
    return readJson("/samples/linked-data/instance2.json");
  }

  public static Map<String, Object> getWorkSampleAsMap() {
    return readJson("/samples/linked-data/work.json");
  }

  public static Map<String, Object> getWork2SampleAsMap() {
    return readJson("/samples/linked-data/work2.json");
  }

  public static Map<String, Object> getHubSampleAsMap() {
    return readJson("/samples/linked-data/hub.json");
  }

  public static Map<String, Object> getHubSample2AsMap() {
    return readJson("/samples/linked-data/hub2.json");
  }

  private static Map<String, Object> readJson(String path) {
    return readJsonFromFile(path, MAP_TYPE_REFERENCE);
  }
}
