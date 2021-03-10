package org.folio.search.sample;

import static java.util.Arrays.asList;
import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;
import static org.folio.search.utils.TestUtils.readJsonFromFile;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.folio.search.domain.dto.Holding;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SampleInstances {
  private static final Instance SEMANTIC_WEB = readSampleInstance("semantic-web-primer");

  public static Instance getSemanticWeb() {
    return OBJECT_MAPPER.convertValue(SEMANTIC_WEB, Instance.class);
  }

  private static Instance readSampleInstance(String sampleName) {
    var path = "/samples/" + sampleName + "/";
    var instance = readJsonFromFile(path + "instance.json", Instance.class);
    var hrs = readJsonFromFile(path + "holdings.json", Holding[].class);
    var items = readJsonFromFile(path + "items.json", Item[].class);

    return instance.holdings(asList(hrs)).items(asList(items));
  }
}
