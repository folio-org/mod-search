package org.folio.search.service.browse;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.folio.search.model.SearchResult;
import org.folio.search.model.index.ContributorResource;
import org.folio.search.model.index.InstanceSubResource;
import org.folio.spring.test.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ContributorBrowseServiceTest {

  private final ContributorBrowseService service = new ContributorBrowseService();

  @Test
  void mapToBrowseResult_positive_shouldCalculateTotalInstances() {
    var contributors = singletonList(ContributorResource.builder()
      .name("test")
      .contributorTypeId(Set.of("test1"))
      .contributorNameTypeId("test2")
      .authorityId("test3")
      .instances(Set.of(
        contributorInstance("test4|1"),
        contributorInstance("test5|2"),
        contributorInstance("test4|3")))
      .build());
    var searchResult = new SearchResult<ContributorResource>();
    searchResult.setTotalRecords(1);
    searchResult.setRecords(contributors);

    var browseResult = service.mapToBrowseResult(searchResult, false);

    assertThat(browseResult.getRecords().get(0).getTotalRecords())
      .isEqualTo(2);
  }

  private InstanceSubResource contributorInstance(String instanceId) {
    return InstanceSubResource.builder().instanceId(instanceId).build();
  }
}
