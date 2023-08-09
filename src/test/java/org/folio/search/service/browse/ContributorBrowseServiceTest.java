package org.folio.search.service.browse;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.opensearch.index.query.QueryBuilders.termQuery;

import java.util.List;
import java.util.Set;
import org.folio.search.domain.dto.InstanceContributorBrowseItem;
import org.folio.search.model.SearchResult;
import org.folio.search.model.index.ContributorResource;
import org.folio.search.model.index.InstanceSubResource;
import org.folio.search.model.service.BrowseContext;
import org.folio.spring.test.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ContributorBrowseServiceTest {

  private final ContributorBrowseService service = new ContributorBrowseService();

  @Test
  void mapToBrowseResult_positive_withConsortiumFilter() {
    var contributors = contributors();
    var searchResult = new SearchResult<ContributorResource>();
    searchResult.setTotalRecords(1);
    searchResult.setRecords(contributors);
    var browseContext = BrowseContext.builder()
      .filters(singletonList(termQuery("instances.shared", false)))
      .build();

    var expected = new InstanceContributorBrowseItem()
      .isAnchor(false)
      .contributorTypeId(List.of("type1", "type2"))
      .name("name")
      .contributorNameTypeId("nameType")
      .authorityId("auth")
      .totalRecords(1);

    var browseResult = service.mapToBrowseResult(browseContext, searchResult, false);

    assertThat(browseResult.getRecords().get(0))
      .isEqualTo(expected);
  }

  @Test
  void mapToBrowseResult_positive() {
    var contributors = contributors();
    var searchResult = new SearchResult<ContributorResource>();
    searchResult.setTotalRecords(1);
    searchResult.setRecords(contributors);
    var browseContext = BrowseContext.builder().build();

    var expected = new InstanceContributorBrowseItem()
      .isAnchor(false)
      .contributorTypeId(List.of("type1", "type2"))
      .name("name")
      .contributorNameTypeId("nameType")
      .authorityId("auth")
      .totalRecords(2);

    var browseResult = service.mapToBrowseResult(browseContext, searchResult, false);

    assertThat(browseResult.getRecords().get(0))
      .isEqualTo(expected);
  }

  private List<ContributorResource> contributors() {
    return singletonList(ContributorResource.builder()
      .name("name")
      .contributorNameTypeId("nameType")
      .authorityId("auth")
      .instances(Set.of(
        contributorInstance("ins1", "type1", false, "tenant1"),
        contributorInstance("ins2", "type1", true, "tenant2"),
        contributorInstance("ins1", "type2", false, "tenant1")))
      .build());
  }

  private InstanceSubResource contributorInstance(String instanceId, String typeId, Boolean shared, String tenantId) {
    return InstanceSubResource.builder()
      .instanceId(instanceId)
      .typeId(typeId)
      .shared(shared)
      .tenantId(tenantId)
      .build();
  }
}
