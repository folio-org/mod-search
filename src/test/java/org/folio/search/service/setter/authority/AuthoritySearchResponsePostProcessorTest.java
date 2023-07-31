package org.folio.search.service.setter.authority;

import static org.apache.lucene.search.TotalHits.Relation.EQUAL_TO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.index.query.QueryBuilders.termQuery;

import java.util.List;
import java.util.Optional;
import org.apache.lucene.search.TotalHits;
import org.folio.search.domain.dto.Authority;
import org.folio.search.model.SimpleResourceRequest;
import org.folio.search.model.index.AuthRefType;
import org.folio.search.repository.SearchRepository;
import org.folio.search.service.consortium.ConsortiumTenantService;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.folio.search.support.base.TenantConfig;
import org.folio.spring.FolioExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.action.search.MultiSearchResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.search.SearchHits;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;

@ExtendWith(MockitoExtension.class)
@SpringBootTest(classes = TenantConfig.class)
class AuthoritySearchResponsePostProcessorTest {

  private @MockBean SearchRepository searchRepository;
  private @MockBean SearchFieldProvider searchFieldProvider;
  private @MockBean FolioExecutionContext context;
  private @MockBean MultiSearchResponse multiSearchResponse;
  private @MockBean ConsortiumTenantService consortiumTenantService;
  private @SpyBean AuthoritySearchResponsePostProcessor processor;

  @Autowired
  private String centralTenant;

  private @Captor ArgumentCaptor<List<SearchSourceBuilder>> mSearchCaptor;

  @BeforeEach
  void setUp() {

  }

  @Test
  void shouldDoNothing_whenProcessNotAuthorizedAuthorities() {
    var authority1 = getAuthority("1", AuthRefType.REFERENCE);
    var authority2 = getAuthority("2", AuthRefType.AUTH_REF);

    processor.process(List.of(authority1, authority2));

    assertThat(authority1).extracting(Authority::getNumberOfTitles).isNull();
    assertThat(authority2).extracting(Authority::getNumberOfTitles).isNull();
  }

  private static Authority getAuthority(String id, AuthRefType reference) {
    return new Authority().id(id).authRefType(reference.getTypeValue());
  }

  @Test
  void shouldSetNumberOfTitles_whenProcessAuthorizedAuthoritiesThatHaveInstanceReferences() {
    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(consortiumTenantService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(centralTenant));
    when(searchFieldProvider.getFields("instance", "authorityId")).thenReturn(List.of("f1", "f2"));
    mockSearchResponse(10, 11);

    var authority1 = getAuthority("1", AuthRefType.AUTHORIZED);
    var authority2 = getAuthority("2", AuthRefType.AUTHORIZED);
    processor.process(List.of(authority1, authority2));

    assertThat(authority1).extracting(Authority::getNumberOfTitles).isEqualTo(10);
    assertThat(authority2).extracting(Authority::getNumberOfTitles).isEqualTo(11);

    verify(searchRepository).msearch(
      eq(SimpleResourceRequest.of("instance", centralTenant)), mSearchCaptor.capture());
    var searchSources = mSearchCaptor.getValue();
    assertThat(searchSources)
      .hasSize(2)
      .extracting(SearchSourceBuilder::query)
      .map(query -> (BoolQueryBuilder) query)
      .allMatch(query -> query.should().size() == 2)
      .allMatch(query -> query.minimumShouldMatch().equals("1"))
      .allMatch(query -> query.must().get(0).equals(affiliationQuery(TENANT_ID, true)));
  }

  @Test
  void shouldSetNumberOfTitlesTo0_whenProcessAuthorizedAuthoritiesThatDoNotHaveInstanceReferences() {

    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(consortiumTenantService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(centralTenant));
    when(searchFieldProvider.getFields("instance", "authorityId")).thenReturn(List.of("f1", "f2"));
    mockSearchResponse(0, null);

    var authority1 = getAuthority("1", AuthRefType.AUTHORIZED);
    var authority2 = getAuthority("2", AuthRefType.AUTHORIZED);
    processor.process(List.of(authority1, authority2));

    assertThat(authority1).extracting(Authority::getNumberOfTitles).isEqualTo(0);
    assertThat(authority2).extracting(Authority::getNumberOfTitles).isEqualTo(0);
  }

  @Test
  void shouldSetNumberOfTitles_whenNotInConsortium() {
    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(searchFieldProvider.getFields("instance", "authorityId")).thenReturn(List.of("f1", "f2"));
    mockSearchResponse(10, 11);

    var authority1 = getAuthority("1", AuthRefType.AUTHORIZED);
    var authority2 = getAuthority("2", AuthRefType.AUTHORIZED);
    processor.process(List.of(authority1, authority2));

    verify(searchRepository).msearch(
      eq(SimpleResourceRequest.of("instance", centralTenant)), mSearchCaptor.capture());
    var searchSources = mSearchCaptor.getValue();
    assertThat(searchSources)
      .hasSize(2)
      .extracting(SearchSourceBuilder::query)
      .map(query -> (BoolQueryBuilder) query)
      .allMatch(query -> query.should().size() == 2)
      .allMatch(query -> query.must().size() == 0);
  }

  @Test
  void shouldSetNumberOfTitles_whenCentralTenant() {
    when(context.getTenantId()).thenReturn(centralTenant);
    when(consortiumTenantService.getCentralTenant(centralTenant)).thenReturn(Optional.of(centralTenant));
    when(searchFieldProvider.getFields("instance", "authorityId")).thenReturn(List.of("f1", "f2"));
    mockSearchResponse(10, 11);

    var authority1 = getAuthority("1", AuthRefType.AUTHORIZED);
    var authority2 = getAuthority("2", AuthRefType.AUTHORIZED);
    processor.process(List.of(authority1, authority2));

    verify(searchRepository).msearch(
      eq(SimpleResourceRequest.of("instance", centralTenant)), mSearchCaptor.capture());
    var searchSources = mSearchCaptor.getValue();
    assertThat(searchSources)
      .hasSize(2)
      .extracting(SearchSourceBuilder::query)
      .map(query -> (BoolQueryBuilder) query)
      .allMatch(query -> query.should().size() == 2)
      .allMatch(query -> query.minimumShouldMatch().equals("1"))
      .allMatch(query -> query.must().get(0).equals(affiliationQuery(centralTenant, null)));
  }

  private void mockSearchResponse(Integer... counts) {
    var searchResponse = mock(SearchResponse.class);
    when(searchRepository.msearch(eq(SimpleResourceRequest.of("instance", centralTenant)), any()))
      .thenReturn(multiSearchResponse);
    var hitsOngoingStubbing = when(searchResponse.getHits());
    var responses = new MultiSearchResponse.Item[counts.length];
    for (int i = 0; i < counts.length; i++) {
      responses[i] = new MultiSearchResponse.Item(searchResponse, null);
      var searchHits = new SearchHits(null, counts[i] != null ? new TotalHits(counts[i], EQUAL_TO) : null, 1.0f);
      hitsOngoingStubbing = hitsOngoingStubbing.thenReturn(searchHits);
    }
    when(multiSearchResponse.getResponses()).thenReturn(responses);
  }

  private BoolQueryBuilder affiliationQuery(String tenantId, Boolean shared) {
    var query = boolQuery().should(termQuery("tenantId", tenantId));
    if (shared != null) {
      query.should(termQuery("shared", shared));
    }
    return query;
  }
}
