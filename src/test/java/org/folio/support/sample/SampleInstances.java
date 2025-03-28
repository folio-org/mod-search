package org.folio.support.sample;

import static org.folio.support.utils.JsonTestUtils.readJsonFromFile;
import static org.folio.support.utils.JsonTestUtils.readJsonFromFileAsMap;
import static org.folio.support.utils.JsonTestUtils.toObject;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.folio.search.domain.dto.Instance;
import org.springframework.test.web.servlet.ResultMatcher;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SampleInstances {

  private static final Map<String, Object> SEMANTIC_WEB_OBJECT = readSampleInstance();
  private static final Instance SEMANTIC_WEB = toObject(SEMANTIC_WEB_OBJECT, Instance.class);
  private static final String SEMANTIC_WEB_ID = SEMANTIC_WEB.getId();

  /**
   * Return new instance object each call.
   */
  public static Instance getSemanticWeb() {
    return toObject(SEMANTIC_WEB_OBJECT, Instance.class);
  }

  public static Map<String, Object> getSemanticWebAsMap() {
    return SEMANTIC_WEB_OBJECT;
  }

  public static String getSemanticWebId() {
    return SEMANTIC_WEB_ID;
  }

  public static List<ResultMatcher> getSemanticWebMatchers() {
    var holdingsMatcher = jsonPath("$.instances.[0].holdings.size()", is(SEMANTIC_WEB.getHoldings().size()));
    var itemsMatcher = jsonPath("$.instances.[0].items.size()", is(SEMANTIC_WEB.getItems().size()));
    return List.of(holdingsMatcher, itemsMatcher);
  }

  private static Map<String, Object> readSampleInstance() {
    var path = "/samples/semantic-web-primer/";
    var instance = readJsonFromFileAsMap(path + "instance.json");
    var hrs = readJsonFromFile(path + "holdings.json", new TypeReference<List<Map<String, Object>>>() { });
    var items = readJsonFromFile(path + "items.json", new TypeReference<List<Map<String, Object>>>() { });
    instance.put("holdings", hrs);
    instance.put("items", items);

    return instance;
  }
}
