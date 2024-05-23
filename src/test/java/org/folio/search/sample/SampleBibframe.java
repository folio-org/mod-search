package org.folio.search.sample;

import static org.folio.search.utils.JsonConverter.MAP_TYPE_REFERENCE;
import static org.folio.search.utils.TestUtils.readJsonFromFile;

import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SampleBibframe {

  private static final Map<String, Object> BIBFRAME_AS_MAP =
    readJsonFromFile("/samples/bibframe/bibframe.json", MAP_TYPE_REFERENCE);

  private static final Map<String, Object> BIBFRAME_2_AS_MAP =
    readJsonFromFile("/samples/bibframe/bibframe2.json", MAP_TYPE_REFERENCE);

  public static Map<String, Object> getBibframeSampleAsMap() {
    return BIBFRAME_AS_MAP;
  }

  public static Map<String, Object> getBibframe2SampleAsMap() {
    return BIBFRAME_2_AS_MAP;
  }
}
