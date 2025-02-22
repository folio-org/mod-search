package org.folio.search.service.browse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.model.types.ResourceType.AUTHORITY;
import static org.folio.search.model.types.ResponseGroupType.BROWSE;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.authorityBrowseItem;
import static org.folio.search.utils.TestUtils.searchResult;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.index.query.QueryBuilders.disMaxQuery;
import static org.opensearch.index.query.QueryBuilders.rangeQuery;
import static org.opensearch.index.query.QueryBuilders.termQuery;
import static org.opensearch.index.query.QueryBuilders.termsQuery;
import static org.opensearch.search.sort.SortBuilders.fieldSort;
import static org.opensearch.search.sort.SortOrder.ASC;
import static org.opensearch.search.sort.SortOrder.DESC;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.AuthorityBrowseItem;
import org.folio.search.model.BrowseResult;
import org.folio.search.model.ResourceRequest;
import org.folio.search.model.SearchResult;
import org.folio.search.model.service.BrowseContext;
import org.folio.search.model.service.BrowseRequest;
import org.folio.search.repository.SearchRepository;
import org.folio.search.service.consortium.ConsortiumSearchHelper;
import org.folio.search.service.converter.ElasticsearchDocumentConverter;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.action.search.MultiSearchResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.SortOrder;

@UnitTest
@ExtendWith(MockitoExtension.class)
class AuthorityBrowseServiceTest {

  private static final String TARGET_FIELD = "headingRef";

  @InjectMocks
  private AuthorityBrowseService authorityBrowseService;
  @Mock
  private SearchRepository searchRepository;
  @Mock
  private BrowseContextProvider browseContextProvider;
  @Mock
  private ElasticsearchDocumentConverter documentConverter;
  @Mock
  private SearchFieldProvider searchFieldProvider;
  @Mock
  private ConsortiumSearchHelper consortiumSearchHelper;
  @Mock
  private SearchResponse searchResponse;

  @BeforeEach
  void setUp() {
    doAnswer(invocation -> invocation.getArgument(0))
      .when(consortiumSearchHelper).filterQueryForActiveAffiliation(any(), any());
    authorityBrowseService.setDocumentConverter(documentConverter);
    authorityBrowseService.setSearchRepository(searchRepository);
    authorityBrowseService.setBrowseContextProvider(browseContextProvider);
    authorityBrowseService.setSearchResponsePostProcessors(Collections.emptyMap());
    lenient().when(searchRepository.analyze(any(), any(), any(), any()))
      .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void browse_positive_forward() {
    var query = "headingRef > s0";
    var request = BrowseRequest.of(AUTHORITY, TENANT_ID, query, 5, TARGET_FIELD, null, true, false, 5);
    var esQuery = rangeQuery(TARGET_FIELD).gt("s0");
    var context = BrowseContext.builder().succeedingQuery(esQuery).succeedingLimit(5).anchor("s0").build();

    when(browseContextProvider.get(request)).thenReturn(context);
    when(searchRepository.search(request, searchSource("s0", 6, ASC))).thenReturn(searchResponse);
    when(documentConverter.convertToSearchResult(searchResponse, Authority.class))
      .thenReturn(searchResult(authorities("s1", "s2", "s3", "s4", "s5")));

    var browseSearchResult = authorityBrowseService.browse(request);

    assertThat(browseSearchResult).isEqualTo(BrowseResult.of(5, "s1", null, List.of(
      browseItem("s1"), browseItem("s2"), browseItem("s3"), browseItem("s4"), browseItem("s5"))));
  }

  @Test
  void browse_positive_forward_expandAllIsFalse() {
    var query = "headingRef > s0";
    var request = BrowseRequest.of(AUTHORITY, TENANT_ID, query, 5, TARGET_FIELD, null, false, false, 5);
    var esQuery = rangeQuery(TARGET_FIELD).gt("s0");
    var context = BrowseContext.builder().succeedingQuery(esQuery).succeedingLimit(5).anchor("s0").build();
    var expectedSearchSource = searchSource("s0", 6, ASC).fetchSource(new String[] {"id", "headingRef"}, null);

    when(browseContextProvider.get(request)).thenReturn(context);
    when(searchRepository.search(request, expectedSearchSource)).thenReturn(searchResponse);
    when(searchFieldProvider.getSourceFields(AUTHORITY, BROWSE)).thenReturn(new String[] {"id", "headingRef"});
    when(documentConverter.convertToSearchResult(searchResponse, Authority.class))
      .thenReturn(searchResult(authorities("s1", "s2", "s3", "s4", "s5")));

    var browseSearchResult = authorityBrowseService.browse(request);

    assertThat(browseSearchResult).isEqualTo(BrowseResult.of(5, "s1", null, List.of(
      browseItem("s1"), browseItem("s2"), browseItem("s3"), browseItem("s4"), browseItem("s5"))));
  }

  @Test
  void browse_positive_backward() {
    var query = "headingRef < s4";
    var request = BrowseRequest.of(AUTHORITY, TENANT_ID, query, 3, TARGET_FIELD, null, true, false, 3);
    var esQuery = rangeQuery(TARGET_FIELD).lt("s4");
    var context = BrowseContext.builder().precedingQuery(esQuery).precedingLimit(3).anchor("s4").build();

    when(browseContextProvider.get(request)).thenReturn(context);
    when(searchRepository.search(request, searchSource("s4", 4, DESC))).thenReturn(searchResponse);
    when(documentConverter.convertToSearchResult(searchResponse, Authority.class))
      .thenReturn(searchResult(authorities("s3", "s2", "s1")));

    var browseSearchResult = authorityBrowseService.browse(request);

    assertThat(browseSearchResult).isEqualTo(BrowseResult.of(3, null, "s3", List.of(
      browseItem("s1"), browseItem("s2"), browseItem("s3"))));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void browse_positive_forwardIncluding(boolean highlightMatch) {
    var query = "headingRef >= s0";
    var request = BrowseRequest.of(AUTHORITY, TENANT_ID, query, 5, TARGET_FIELD, null, true, highlightMatch, 5);
    var esQuery = rangeQuery(TARGET_FIELD).gte("s0");
    var context = BrowseContext.builder().succeedingQuery(esQuery).succeedingLimit(5).anchor("s0").build();

    when(browseContextProvider.get(request)).thenReturn(context);
    mockMultiSearchRequest(request,
      List.of(searchSource("s0", 6, ASC), anchorSearchSource("s0", 5)),
      List.of(searchResult(authorities("s1", "s2", "s3", "s4", "s5")), searchResult(authority("s0"))));

    var browseSearchResult = authorityBrowseService.browse(request);

    assertThat(browseSearchResult).isEqualTo(BrowseResult.of(5, "s0", "s4", List.of(
      browseItem("s0"), browseItem("s1"), browseItem("s2"), browseItem("s3"), browseItem("s4"))));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void browse_positive_forwardIncluding_searchResultLowerThanLimit(boolean highlightMatch) {
    var query = "headingRef >= s0";
    var request = BrowseRequest.of(AUTHORITY, TENANT_ID, query, 10, TARGET_FIELD, null, true, highlightMatch, 5);
    var esQuery = rangeQuery(TARGET_FIELD).gte("s0");
    var context = BrowseContext.builder().succeedingQuery(esQuery).succeedingLimit(10).anchor("s0").build();

    when(browseContextProvider.get(request)).thenReturn(context);
    mockMultiSearchRequest(request,
      List.of(searchSource("s0", 11, ASC), anchorSearchSource("s0", 10)),
      List.of(SearchResult.of(10, authorities("s1", "s2", "s3")), searchResult(authority("s0"))));

    var browseSearchResult = authorityBrowseService.browse(request);

    assertThat(browseSearchResult).isEqualTo(BrowseResult.of(10, "s0", null, List.of(
      browseItem("s0"), browseItem("s1"), browseItem("s2"), browseItem("s3"))));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void browse_positive_forwardIncludingAnchorNotFound(boolean highlightMatch) {
    var query = "headingRef >= s0";
    var request = BrowseRequest.of(AUTHORITY, TENANT_ID, query, 5, TARGET_FIELD, null, true, highlightMatch, 5);
    var esQuery = rangeQuery(TARGET_FIELD).gte("s0");
    var context = BrowseContext.builder().succeedingQuery(esQuery).succeedingLimit(5).anchor("s0").build();

    when(browseContextProvider.get(request)).thenReturn(context);
    mockMultiSearchRequest(request,
      List.of(searchSource("s0", 6, ASC), anchorSearchSource("s0", 5)),
      List.of(searchResult(authorities("s1", "s2", "s3", "s4", "s5", "s6")), SearchResult.empty()));

    var browseSearchResult = authorityBrowseService.browse(request);

    assertThat(browseSearchResult).isEqualTo(BrowseResult.of(6, "s1", "s5", List.of(
      browseItem("s1"), browseItem("s2"), browseItem("s3"), browseItem("s4"), browseItem("s5"))));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void browse_positive_backwardIncluding(boolean highlightMatch) {
    var query = "headingRef <= s4";
    var request = BrowseRequest.of(AUTHORITY, TENANT_ID, query, 3, TARGET_FIELD, null, true, highlightMatch, 3);
    var esQuery = rangeQuery(TARGET_FIELD).lte("s4");
    var context = BrowseContext.builder().precedingQuery(esQuery).precedingLimit(3).anchor("s4").build();

    when(browseContextProvider.get(request)).thenReturn(context);
    mockMultiSearchRequest(request,
      List.of(searchSource("s4", 4, DESC), anchorSearchSource("s4", 3)),
      List.of(searchResult(authorities("s3", "s2", "s1")), searchResult(authority("s4"))));

    var browseSearchResult = authorityBrowseService.browse(request);

    assertThat(browseSearchResult).isEqualTo(BrowseResult.of(3, "s2", "s4", List.of(
      browseItem("s2"), browseItem("s3"), browseItem("s4"))));
  }

  @Test
  void browse_positive_around() {
    var query = "headingRef > s0 or headingRef < s0";
    var request = BrowseRequest.of(AUTHORITY, TENANT_ID, query, 5, TARGET_FIELD, null, true, true, 2);

    when(browseContextProvider.get(request)).thenReturn(browseContextAround(false));
    mockMultiSearchRequest(request,
      List.of(searchSource("s0", 3, DESC), searchSource("s0", 4, ASC)),
      List.of(SearchResult.of(10, authorities("r2", "r1")), searchResult(authorities("s1", "s2", "s3"))));

    var actual = authorityBrowseService.browse(request);
    assertThat(actual).isEqualTo(BrowseResult.of(10, null, "s2", List.of(
      browseItem("r1"), browseItem("r2"), authorityBrowseItem("s0", null, true), browseItem("s1"), browseItem("s2"))));
  }

  @Test
  void browse_positive_aroundWithoutHighlighting() {
    var query = "headingRef > s0 or headingRef < s0";
    var request = BrowseRequest.of(AUTHORITY, TENANT_ID, query, 5, TARGET_FIELD, null, true, false, 2);

    when(browseContextProvider.get(request)).thenReturn(browseContextAround(false));
    mockMultiSearchRequest(request,
      List.of(searchSource("s0", 3, DESC), searchSource("s0", 4, ASC)),
      List.of(SearchResult.of(10, authorities("r2", "r1", "r0")), searchResult(authorities("s1", "s2", "s3", "s4"))));

    var actual = authorityBrowseService.browse(request);
    assertThat(actual).isEqualTo(BrowseResult.of(10, "r1", "s3", List.of(
      browseItem("r1"), browseItem("r2"), browseItem("s1"), browseItem("s2"), browseItem("s3"))));
  }

  @Test
  void browse_positive_aroundIncluding() {
    var query = "headingRef < s0 or headingRef >= s0";
    var request = BrowseRequest.of(AUTHORITY, TENANT_ID, query, 5, TARGET_FIELD, null, true, true, 2);

    when(browseContextProvider.get(request)).thenReturn(browseContextAround(true));
    mockMultiSearchRequest(request,
      List.of(anchorSearchSource("s0", 3)),
      List.of(searchResult(authority("s0"))));
    mockMultiSearchRequest(request,
      List.of(searchSource("s0", 3, DESC), searchSource("s0", 4, ASC)),
      List.of(
        SearchResult.of(10, authorities("r2", "r1", "r0")),
        searchResult(authorities("s1", "s2", "s3", "s4"))));

    var actual = authorityBrowseService.browse(request);
    assertThat(actual).isEqualTo(BrowseResult.of(10, "r1", "s2", List.of(
      browseItem("r1"), browseItem("r2"), browseItem("s0").isAnchor(true), browseItem("s1"), browseItem("s2"))));
  }

  @Test
  void browse_positive_aroundIncludingWithoutHighlighting() {
    var query = "headingRef < s0 or headingRef >= s0";
    var request = BrowseRequest.of(AUTHORITY, TENANT_ID, query, 5, TARGET_FIELD, null, true, false, 2);

    when(browseContextProvider.get(request)).thenReturn(browseContextAround(true));
    mockMultiSearchRequest(request,
      List.of(anchorSearchSource("s0", 3)),
      List.of(searchResult(authority("s0"))));
    mockMultiSearchRequest(request,
      List.of(searchSource("s0", 3, DESC), searchSource("s0", 4, ASC)),
      List.of(
        SearchResult.of(10, authorities("r2", "r1")),
        searchResult(authorities("s1", "s2", "s3", "s4"))));

    var actual = authorityBrowseService.browse(request);
    assertThat(actual).isEqualTo(BrowseResult.of(10, null, "s2", List.of(
      browseItem("r1"), browseItem("r2"), browseItem("s0"), browseItem("s1"), browseItem("s2"))));
  }

  @Test
  void browse_positive_aroundIncludingMissingAnchor() {
    var query = "headingRef <= s0 or headingRef > s0";
    var request = BrowseRequest.of(AUTHORITY, TENANT_ID, query, 5, TARGET_FIELD, null, true, true, 2);

    when(browseContextProvider.get(request)).thenReturn(browseContextAround(true));
    mockMultiSearchRequest(request,
      List.of(anchorSearchSource("s0", 3)),
      List.of(SearchResult.empty()));
    mockMultiSearchRequest(request,
      List.of(searchSource("s0", 3, DESC), searchSource("s0", 4, ASC)),
      List.of(
        SearchResult.of(10, authorities("r2", "r1")),
        searchResult(authorities("s1", "s2", "s3"))));

    var actual = authorityBrowseService.browse(request);
    assertThat(actual).isEqualTo(BrowseResult.of(10, null, "s2", List.of(
      browseItem("r1"), browseItem("r2"), authorityBrowseItem("s0", null, true), browseItem("s1"), browseItem("s2"))));
  }

  @Test
  void browse_positive_aroundIncludingMissingAnchorWithoutHighlighting() {
    var query = "headingRef <= s0 or headingRef > s0";
    var request = BrowseRequest.of(AUTHORITY, TENANT_ID, query, 5, TARGET_FIELD, null, true, false, 2);

    when(browseContextProvider.get(request)).thenReturn(browseContextAround(true));
    mockMultiSearchRequest(request,
      List.of(anchorSearchSource("s0", 3)),
      List.of(SearchResult.empty()));
    mockMultiSearchRequest(request,
      List.of(searchSource("s0", 3, DESC), searchSource("s0", 4, ASC)),
      List.of(
        SearchResult.of(10, authorities("r2", "r1")),
        searchResult(authorities("s1", "s2", "s3"))));

    var actual = authorityBrowseService.browse(request);
    assertThat(actual).isEqualTo(BrowseResult.of(10, null, null, List.of(
      browseItem("r1"), browseItem("r2"), browseItem("s1"), browseItem("s2"), browseItem("s3"))));
  }

  @Test
  void getSearchQuery_positive_consortium() {
    var query = disMaxQuery();
    when(consortiumSearchHelper.filterQueryForActiveAffiliation(any(), any())).thenReturn(query);

    var actual = authorityBrowseService.getSearchQuery(
      BrowseRequest.builder().targetField("test").build(),
      BrowseContext.builder().anchor("test").succeedingLimit(1).build(), true);
    assertThat(actual.query()).isEqualTo(query);
  }

  @Test
  void getAnchorSearchQuery_positive_consortium() {
    var query = disMaxQuery();
    when(consortiumSearchHelper.filterQueryForActiveAffiliation(any(), any())).thenReturn(query);

    var actual = authorityBrowseService.getAnchorSearchQuery(
      BrowseRequest.builder().targetField("test").build(),
      BrowseContext.builder().anchor("test").succeedingLimit(1).precedingLimit(1).build());
    assertThat(actual.query()).isEqualTo(query);
  }

  private static SearchSourceBuilder searchSource(String heading, int size, SortOrder sortOrder) {
    return SearchSourceBuilder.searchSource()
      .query(boolQuery().filter(termsQuery("authRefType", "Authorized", "Reference")))
      .searchAfter(new String[] {heading}).from(0).size(size)
      .sort(fieldSort(TARGET_FIELD).order(sortOrder))
      .fetchSource((String[]) null, null);
  }

  private static SearchSourceBuilder searchSource(QueryBuilder query) {
    return SearchSourceBuilder.searchSource().query(query).fetchSource((String[]) null, null);
  }

  private static SearchSourceBuilder anchorSearchSource(String headingRef, int size) {
    var query = boolQuery()
      .filter(termsQuery("authRefType", "Authorized", "Reference"))
      .must(termQuery(TARGET_FIELD, headingRef));
    return searchSource(query).from(0).size(size).sort(TARGET_FIELD).fetchSource((String[]) null, null);
  }

  private void mockMultiSearchRequest(ResourceRequest request,
                                      List<SearchSourceBuilder> queries, List<SearchResult<Authority>> results) {
    var multiSearchResponse = mock(MultiSearchResponse.class);
    var items = new MultiSearchResponse.Item[results.size()];
    for (int i = 0; i < results.size(); i++) {
      items[i] = mock(MultiSearchResponse.Item.class);
      var searchResponseMock = mock(SearchResponse.class);
      when(items[i].getResponse()).thenReturn(searchResponseMock);
      when(documentConverter.convertToSearchResult(searchResponseMock, Authority.class)).thenReturn(results.get(i));
    }

    when(searchRepository.msearch(request, queries)).thenReturn(multiSearchResponse);
    when(multiSearchResponse.getResponses()).thenReturn(items);
  }

  private static List<Authority> authorities(String... values) {
    return Arrays.stream(values).map(AuthorityBrowseServiceTest::authority).toList();
  }

  private static Authority authority(String heading) {
    return new Authority().headingRef(heading);
  }

  private static AuthorityBrowseItem browseItem(String heading) {
    return new AuthorityBrowseItem().authority(authority(heading)).headingRef(heading);
  }

  private static BrowseContext browseContextAround(boolean includeAnchor) {
    var precedingQuery = rangeQuery(TARGET_FIELD).lt("s0");
    var succeedingQuery = includeAnchor ? rangeQuery(TARGET_FIELD).gte("s0") : rangeQuery(TARGET_FIELD).gt("s0");
    return BrowseContext.builder().precedingQuery(precedingQuery).precedingLimit(2)
      .succeedingQuery(succeedingQuery).succeedingLimit(3).anchor("s0").build();
  }
}
