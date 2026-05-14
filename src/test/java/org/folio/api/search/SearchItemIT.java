package org.folio.api.search;

import static org.folio.support.sample.SampleInstances.getSemanticWebId;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.folio.support.base.BaseSharedTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public abstract class SearchItemIT extends BaseSharedTest {

  @CsvSource({
    "items.fullCallNumber=={value}, REF TK51*",
    "items.effectiveCallNumberComponents=={value}, *c.2",
    "items.normalizedCallNumbers=={value}, REF",
    "items.normalizedCallNumbers=={value}, TK5105.88815.A58 2004 FT MEADE",
    "items.normalizedCallNumbers=={value}, TK5105.88815",
    "item.fullCallNumber=={value}, REF TK51*",
    "item.effectiveCallNumberComponents=={value}, *c.2",
    "item.normalizedCallNumbers=={value}, REF",
    "item.normalizedCallNumbers=={value}, TK5105.88815.A58 2004 FT MEADE",
    "item.normalizedCallNumbers=={value}, TK5105.88815",
    "itemNormalizedCallNumbers=={value}, REF",
    "itemNormalizedCallNumbers=={value}, TK5105.88815.A58 2004 FT MEADE",
    "itemNormalizedCallNumbers=={value}, TK5105.88815",
    "itemNormalizedCallNumbers=={value}, REF TK510588815",
    "itemNormalizedCallNumbers=={value}, tk510588815",
    "itemNormalizedCallNumbers=={value}, TK5105.88815.A58 2004 FT MEADE c.2",
    "itemNormalizedCallNumbers=={value}, TK510588815A582004FT MEADE c2"
  })
  @ParameterizedTest(name = "[{index}] {0}: {1}")
  void canSearchByItems_wildcardMatch(String query, String value) throws Throwable {
    doSearchByInstances(prepareQuery(query, value))
      .andExpect(jsonPath("totalRecords", is(1)))
      .andExpect(jsonPath("instances[0].id", is(getSemanticWebId())));
  }

  @CsvSource({
    "items.normalizedCallNumbers=={value}, 88815 suffix90000",
    "items.normalizedCallNumbers=={value}, prefix TK510588815",
    "items.normalizedCallNumbers=={value}, 510588815",
    "items.normalizedCallNumbers=={value}, TK5105.88815.A58 2004 FT MEADE 90000",
    "item.normalizedCallNumbers=={value}, 88815 suffix90000",
    "item.normalizedCallNumbers=={value}, prefix TK510588815",
    "item.normalizedCallNumbers=={value}, 510588815",
    "item.normalizedCallNumbers=={value}, TK5105.88815.A58 2004 FT MEADE 90000",
    "itemNormalizedCallNumbers=={value}, fix-90000",
    "itemNormalizedCallNumbers=={value}, 90000",
    "itemNormalizedCallNumbers=={value}, 88815.A58 2004 FT MEADE",
    "itemNormalizedCallNumbers=={value}, 88815 suffix90000",
    "itemNormalizedCallNumbers=={value}, prefix TK510588815",
    "itemNormalizedCallNumbers=={value}, 510588815",
    "itemNormalizedCallNumbers=={value}, TK5105.88815.A58 2004 FT MEADE 90000",
  })
  @ParameterizedTest(name = "[{index}] {0}: {1}")
  void canSearchByItems_negative(String query, String value) throws Throwable {
    doSearchByInstances(prepareQuery(query, value))
      .andExpect(jsonPath("totalRecords", is(0)));
  }
}
