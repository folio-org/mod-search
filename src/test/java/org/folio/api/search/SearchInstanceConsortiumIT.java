package org.folio.api.search;

import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.folio.support.sample.SampleInstances.getSemanticWebId;
import static org.folio.support.sample.SampleInstancesResponse.getInstanceBasicResponseSample;
import static org.folio.support.utils.JsonTestUtils.parseResponse;

import org.assertj.core.api.Assertions;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.InstanceSearchResult;
import org.folio.support.base.BaseConsortiumIntegrationTest;
import org.junit.jupiter.api.Test;

public abstract class SearchInstanceConsortiumIT extends BaseConsortiumIntegrationTest {

  @Test
  void responseContainsOnlyBasicInstanceProperties() {
    var expected = getInstanceBasicResponseSample();
    setTenant(expected);
    var response = doSearchByInstances(prepareQuery("id=={value}", getSemanticWebId()));

    var actual = parseResponse(response, InstanceSearchResult.class);

    Assertions.assertThat(actual).usingRecursiveComparison().ignoringCollectionOrder().isEqualTo(expected);
  }

  private static void setTenant(InstanceSearchResult expected) {
    for (Instance instance : expected.getInstances()) {
      instance.setTenantId(MEMBER_TENANT_ID);
    }
  }
}
