package org.folio.search.sample;

import static org.folio.search.utils.JsonConverter.MAP_TYPE_REFERENCE;
import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;
import static org.folio.search.utils.TestUtils.readJsonFromFile;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.folio.search.domain.dto.Instance;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SampleInstances {

  private static final Map<String, Object> SEMANTIC_WEB_OBJECT = readSampleInstance("semantic-web-primer");
  private static final Instance SEMANTIC_WEB = OBJECT_MAPPER.convertValue(SEMANTIC_WEB_OBJECT, Instance.class);
  private static final String SEMANTIC_WEB_ID = SEMANTIC_WEB.getId();

  public static Instance getSemanticWeb() {
    return OBJECT_MAPPER.convertValue(SEMANTIC_WEB, Instance.class);
  }

  public static Map<String, Object> getSemanticWebAsMap() {
    return SEMANTIC_WEB_OBJECT;
  }

  public static String getSemanticWebId() {
    return SEMANTIC_WEB_ID;
  }

  private static Map<String, Object> readSampleInstance(String sampleName) {
    var path = "/samples/" + sampleName + "/";
    var instance = readJsonFromFile(path + "instance.json", MAP_TYPE_REFERENCE);
    var hrs = readJsonFromFile(path + "holdings.json", new TypeReference<List<Map<String, Object>>>() { });
    var items = readJsonFromFile(path + "items.json", new TypeReference<List<Map<String, Object>>>() { });
    instance.put("holdings", hrs);
    instance.put("items", items);

    return instance;
  }
}
