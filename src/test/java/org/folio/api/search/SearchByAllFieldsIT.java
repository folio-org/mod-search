package org.folio.api.search;

import static org.folio.support.sample.SampleInstances.getSemanticWebId;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.base.BaseSharedTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@IntegrationTest
public abstract class SearchByAllFieldsIT extends BaseSharedTest {

  @ValueSource(strings = {
    // instance field values
    "00000008-0000-4000-8000-000000000000",
    "A sem\\\\ntic web primer",
    "Cooperative information systems",
    "0262012103",
    "2003065165",
    "Cambridge, Mass.",
    "Ant\\\\niou, Grigoris matthew",
    "Ant\\\\niou matthew",
    "HD1691 .I5 1967",
    "Cambridge, Mass.",
    "1\\\\9u",
    "*\\9u",
    "1*9u",
    "1\\\\9*",
    "1444",
    "144*",
    "1*44",
    "*444",

    // holding field values
    "00000014-0000-4000-9000-000000000000",
    "ho10000000006",
    "TK5105.88815 . A58 2004 FT MEADE",
    "Includes bibliographical references and index of holdings.",

    // item field values
    "00000004-0000-4000-a000-000000000000",
    "TK5105.88815 . A58 2004 FT MEADE",
    "item000000000014",
    "item_accession_number"
  })
  @ParameterizedTest(name = "[{index}] cql.all='{query}', query=''{0}''")
  void canSearchByAllFieldValues_positive(String cqlQuery) throws Throwable {
    doSearchByInstances(prepareQuery("cql.all=\"{value}\"", cqlQuery), 1, 0)
      .andExpect(jsonPath("totalRecords", is(1)))
      .andExpect(jsonPath("instances[0].id", is(getSemanticWebId())));
  }

  @ValueSource(strings = {
    "00000008-0000-4000-8000-000000000000",
    "A sem\\\\ntic web primer",
    "déjà vu",
    "Cooperative information systems",
    "0262012103",
    "2003065165",
    "Ant\\\\niou, Grigoris matthew",
    "HD1691 .I5 1967",
    "Cambridge, Mass.",
    "2020-12-08T15:47:13.625+0000",
    "1\\\\9u",
    "*\\9u",
    "1*9u",
    "1\\\\9*",
    "1444",
    "144*",
    "1*44",
    "*444"
  })
  @ParameterizedTest(name = "[{index}] cql.allInstances='{query}', query=''{0}''")
  void canSearchByInstanceFieldValues_positive(String cqlQuery) throws Throwable {
    doSearchByInstances(prepareQuery("cql.allInstances=\"{value}\"", cqlQuery), 1, 0)
      .andExpect(jsonPath("totalRecords", is(1)))
      .andExpect(jsonPath("instances[0].id", is(getSemanticWebId())));
  }

  @ValueSource(strings = {
    "00000014-0000-4000-9000-000000000000",
    "ho10000000006",
    "TK5105.88815 . A58 2004 FT MEADE",
    "Includes bibliographical references and index of holdings.",
  })
  @ParameterizedTest(name = "[{index}] cql.allHoldings='{query}', query=''{0}''")
  void canSearchByHoldingFieldValues_positive(String cqlQuery) throws Throwable {
    doSearchByInstances(prepareQuery("cql.allHoldings=\"{value}\"", cqlQuery), 1, 0)
      .andExpect(jsonPath("totalRecords", is(1)))
      .andExpect(jsonPath("instances[0].id", is(getSemanticWebId())));
  }

  @ValueSource(strings = {
    "00000004-0000-4000-a000-000000000000",
    "TK5105.88815 . A58 2004 FT MEADE",
    "item000000000014",
    "item_accession_number"
  })
  @ParameterizedTest(name = "[{index}] cql.allItems='{query}', query=''{0}''")
  void canSearchByItemFieldValues_positive(String cqlQuery) throws Throwable {
    doSearchByInstances(prepareQuery("cql.allItems=\"{value}\"", cqlQuery), 1, 0)
      .andExpect(jsonPath("totalRecords", is(1)))
      .andExpect(jsonPath("instances[0].id", is(getSemanticWebId())));
  }
}
