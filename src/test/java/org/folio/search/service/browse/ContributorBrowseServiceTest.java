package org.folio.search.service.browse;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.opensearch.index.query.QueryBuilders.disMaxQuery;
import static org.opensearch.index.query.QueryBuilders.rangeQuery;
import static org.opensearch.index.query.QueryBuilders.termQuery;

import java.util.List;
import java.util.Set;
import org.folio.search.domain.dto.InstanceContributorBrowseItem;
import org.folio.search.model.SearchResult;
import org.folio.search.model.index.ContributorResource;
import org.folio.search.model.index.InstanceSubResource;
import org.folio.search.model.service.BrowseContext;
import org.folio.search.model.service.BrowseRequest;
import org.folio.search.repository.SearchRepository;
import org.folio.search.service.consortium.ConsortiumSearchHelper;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ContributorBrowseServiceTest {

  @Mock
  private ConsortiumSearchHelper consortiumSearchHelper;
  @Mock
  private SearchRepository searchRepository;

  @InjectMocks
  private ContributorBrowseService service;

  @BeforeEach
  void setUp() {
    service.setSearchRepository(searchRepository);
  }

  @Test
  void mapToBrowseResult_positive() {
    var contributors = contributors();
    var searchResult = new SearchResult<ContributorResource>();
    searchResult.setTotalRecords(1);
    searchResult.setRecords(contributors);
    var browseContext = BrowseContext.builder()
      .filters(singletonList(termQuery("instances.shared", false)))
      .build();
    var instanceId1 = "ins1";
    var instanceId2 = "ins2";
    var contributorsSubResourcesMock = Set.of(
      contributorSubResource(instanceId1, "type1"),
      contributorSubResource(instanceId1, "type2"),
      contributorSubResource(instanceId2, "type1"),
      contributorSubResource(instanceId2, null),
      contributorSubResource(instanceId1, "null"));

    var expected = new InstanceContributorBrowseItem()
      .isAnchor(false)
      .contributorTypeId(List.of("type1", "type2"))
      .name("name")
      .contributorNameTypeId("nameType")
      .authorityId("auth")
      .totalRecords(2);

    when(consortiumSearchHelper.filterSubResourcesForConsortium(any(), any(), any()))
      .thenReturn(contributorsSubResourcesMock);

    var browseResult = service.mapToBrowseResult(browseContext, searchResult, false);

    assertThat(browseResult.getRecords().get(0))
      .isEqualTo(expected);
  }

  @ValueSource(booleans = {true, false})
  @ParameterizedTest
  void getSearchQuery_positive(Boolean isBrowsingForward) {
    var browseRequest = BrowseRequest.builder().targetField("test").build();
    var browseContext = BrowseContext.builder().anchor("test").succeedingLimit(1).precedingLimit(1).build();
    var queryMock = disMaxQuery();

    when(searchRepository.analyze(eq(browseContext.getAnchor()), eq(browseRequest.getTargetField()), any(), any()))
      .thenReturn("test");
    when(consortiumSearchHelper.filterBrowseQueryForActiveAffiliation(eq(browseContext), any(), any()))
      .thenReturn(queryMock);

    var result = service.getSearchQuery(browseRequest, browseContext, isBrowsingForward);

    assertThat(result.query()).isEqualTo(queryMock);
  }

  @Test
  void getAnchorSearchQuery_positive() {
    var browseRequest = BrowseRequest.builder().targetField("test").build();
    var browseContext = BrowseContext.builder()
      .anchor("test").succeedingQuery(rangeQuery("test")).succeedingLimit(1).build();
    var queryMock = disMaxQuery();

    when(consortiumSearchHelper.filterBrowseQueryForActiveAffiliation(eq(browseContext), any(), any()))
      .thenReturn(queryMock);

    var result = service.getAnchorSearchQuery(browseRequest, browseContext);

    assertThat(result.query()).isEqualTo(queryMock);
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

  private InstanceSubResource contributorSubResource(String instanceId, String typeId) {
    return InstanceSubResource.builder()
      .instanceId(instanceId)
      .typeId(typeId)
      .build();
  }
}
