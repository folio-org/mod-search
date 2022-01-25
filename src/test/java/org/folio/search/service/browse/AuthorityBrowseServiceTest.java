package org.folio.search.service.browse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.search.sort.SortBuilders.fieldSort;
import static org.elasticsearch.search.sort.SortOrder.ASC;
import static org.elasticsearch.search.sort.SortOrder.DESC;
import static org.folio.search.utils.SearchUtils.AUTHORITY_RESOURCE;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.authorityBrowseItem;
import static org.folio.search.utils.TestUtils.searchResult;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.elasticsearch.action.search.MultiSearchResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.folio.search.cql.CqlSearchQueryConverter;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.AuthorityBrowseItem;
import org.folio.search.model.ResourceRequest;
import org.folio.search.model.SearchResult;
import org.folio.search.model.service.BrowseRequest;
import org.folio.search.repository.SearchRepository;
import org.folio.search.service.converter.ElasticsearchDocumentConverter;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.folio.search.utils.types.UnitTest;
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
class AuthorityBrowseServiceTest {

  private static final String TARGET_FIELD = "headingRef";

  @InjectMocks private AuthorityBrowseService authorityBrowseService;
  @Mock private SearchRepository searchRepository;
  @Mock private CqlSearchQueryConverter cqlSearchQueryConverter;
  @Mock private ElasticsearchDocumentConverter documentConverter;
  @Mock private SearchFieldProvider searchFieldProvider;
  @Mock private SearchResponse searchResponse;

  @BeforeEach
  void setUp() {
    authorityBrowseService.setDocumentConverter(documentConverter);
    authorityBrowseService.setSearchRepository(searchRepository);
    authorityBrowseService.setCqlSearchQueryConverter(cqlSearchQueryConverter);
  }

  @Test
  void browse_positive_forward() {
    var query = "headingRef > s0";
    var request = BrowseRequest.of(AUTHORITY_RESOURCE, TENANT_ID, query, 5, TARGET_FIELD, true, false, 5);
    var esQuery = rangeQuery(TARGET_FIELD).gt("s0");

    when(cqlSearchQueryConverter.convert(query, AUTHORITY_RESOURCE)).thenReturn(searchSource(esQuery));
    when(searchRepository.search(request, searchSource("s0", 5, ASC))).thenReturn(searchResponse);
    when(documentConverter.convertToSearchResult(searchResponse, Authority.class))
      .thenReturn(searchResult(authorities("s1", "s2", "s3", "s4", "s5")));

    var browseSearchResult = authorityBrowseService.browse(request);

    assertThat(browseSearchResult).isEqualTo(SearchResult.of(5, List.of(
      browseItem("s1"), browseItem("s2"), browseItem("s3"), browseItem("s4"), browseItem("s5"))));
  }

  @Test
  void browse_positive_forward_expandAllIsFalse() {
    var query = "headingRef > s0";
    var request = BrowseRequest.of(AUTHORITY_RESOURCE, TENANT_ID, query, 5, TARGET_FIELD, false, false, 5);
    var esQuery = rangeQuery(TARGET_FIELD).gt("s0");
    var expectedSearchSource = searchSource("s0", 5, ASC).fetchSource(new String[] {"id", "headingRef"}, null);

    when(cqlSearchQueryConverter.convert(query, AUTHORITY_RESOURCE)).thenReturn(searchSource(esQuery));
    when(searchRepository.search(request, expectedSearchSource)).thenReturn(searchResponse);
    when(searchFieldProvider.getSourceFields(AUTHORITY_RESOURCE)).thenReturn(List.of("id", "headingRef"));
    when(documentConverter.convertToSearchResult(searchResponse, Authority.class))
      .thenReturn(searchResult(authorities("s1", "s2", "s3", "s4", "s5")));

    var browseSearchResult = authorityBrowseService.browse(request);

    assertThat(browseSearchResult).isEqualTo(SearchResult.of(5, List.of(
      browseItem("s1"), browseItem("s2"), browseItem("s3"), browseItem("s4"), browseItem("s5"))));
  }

  @Test
  void browse_positive_backward() {
    var query = "headingRef < s4";
    var request = BrowseRequest.of(AUTHORITY_RESOURCE, TENANT_ID, query, 3, TARGET_FIELD, true, false, 3);
    var esQuery = rangeQuery(TARGET_FIELD).lt("s4");

    when(cqlSearchQueryConverter.convert(query, AUTHORITY_RESOURCE)).thenReturn(searchSource(esQuery));
    when(searchRepository.search(request, searchSource("s4", 3, DESC))).thenReturn(searchResponse);
    when(documentConverter.convertToSearchResult(searchResponse, Authority.class))
      .thenReturn(searchResult(authorities("s3", "s2", "s1")));

    var browseSearchResult = authorityBrowseService.browse(request);

    assertThat(browseSearchResult).isEqualTo(SearchResult.of(3, List.of(
      browseItem("s1"), browseItem("s2"), browseItem("s3"))));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void browse_positive_forwardIncluding(boolean highlightMatch) {
    var query = "headingRef >= s0";
    var request = BrowseRequest.of(AUTHORITY_RESOURCE, TENANT_ID, query, 5, TARGET_FIELD, true, highlightMatch, 5);
    var esQuery = rangeQuery(TARGET_FIELD).gte("s0");

    when(cqlSearchQueryConverter.convert(query, AUTHORITY_RESOURCE)).thenReturn(searchSource(esQuery));
    mockMultiSearchRequest(request,
      List.of(searchSource("s0", 5, ASC), anchorSearchSource("s0")),
      List.of(searchResult(authorities("s1", "s2", "s3", "s4", "s5")), searchResult(authority("s0"))));

    var browseSearchResult = authorityBrowseService.browse(request);

    assertThat(browseSearchResult).isEqualTo(SearchResult.of(5, List.of(
      browseItem("s0"), browseItem("s1"), browseItem("s2"), browseItem("s3"), browseItem("s4"))));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void browse_positive_forwardIncluding_searchResultLowerThanLimit(boolean highlightMatch) {
    var query = "headingRef >= s0";
    var request = BrowseRequest.of(AUTHORITY_RESOURCE, TENANT_ID, query, 10, TARGET_FIELD, true, highlightMatch, 5);
    var esQuery = rangeQuery(TARGET_FIELD).gte("s0");

    when(cqlSearchQueryConverter.convert(query, AUTHORITY_RESOURCE)).thenReturn(searchSource(esQuery));
    mockMultiSearchRequest(request,
      List.of(searchSource("s0", 10, ASC), anchorSearchSource("s0")),
      List.of(SearchResult.of(10, authorities("s1", "s2", "s3")), searchResult(authority("s0"))));

    var browseSearchResult = authorityBrowseService.browse(request);

    assertThat(browseSearchResult).isEqualTo(SearchResult.of(10, List.of(
      browseItem("s0"), browseItem("s1"), browseItem("s2"), browseItem("s3"))));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void browse_positive_forwardIncludingAnchorNotFound(boolean highlightMatch) {
    var query = "headingRef >= s0";
    var request = BrowseRequest.of(AUTHORITY_RESOURCE, TENANT_ID, query, 5, TARGET_FIELD, true, highlightMatch, 5);
    var esQuery = rangeQuery(TARGET_FIELD).gte("s0");

    when(cqlSearchQueryConverter.convert(query, AUTHORITY_RESOURCE)).thenReturn(searchSource(esQuery));
    mockMultiSearchRequest(request,
      List.of(searchSource("s0", 5, ASC), anchorSearchSource("s0")),
      List.of(searchResult(authorities("s1", "s2", "s3", "s4", "s5")), SearchResult.empty()));

    var browseSearchResult = authorityBrowseService.browse(request);

    assertThat(browseSearchResult).isEqualTo(SearchResult.of(5, List.of(
      browseItem("s1"), browseItem("s2"), browseItem("s3"), browseItem("s4"), browseItem("s5"))));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void browse_positive_backwardIncluding(boolean highlightMatch) {
    var query = "headingRef <= s4";
    var request = BrowseRequest.of(AUTHORITY_RESOURCE, TENANT_ID, query, 3, TARGET_FIELD, true, highlightMatch, 3);
    var esQuery = rangeQuery(TARGET_FIELD).lte("s4");
    when(cqlSearchQueryConverter.convert(query, AUTHORITY_RESOURCE)).thenReturn(searchSource(esQuery));
    mockMultiSearchRequest(request,
      List.of(searchSource("s4", 3, DESC), anchorSearchSource("s4")),
      List.of(searchResult(authorities("s3", "s2", "s1")), searchResult(authority("s4"))));

    var browseSearchResult = authorityBrowseService.browse(request);

    assertThat(browseSearchResult).isEqualTo(SearchResult.of(3, List.of(
      browseItem("s2"), browseItem("s3"), browseItem("s4"))));
  }

  @Test
  void browse_positive_around() {
    var query = "headingRef > s0 or headingRef < s0";
    var request = BrowseRequest.of(AUTHORITY_RESOURCE, TENANT_ID, query, 5, TARGET_FIELD, true, true, 2);
    var esQuery = boolQuery().should(rangeQuery(TARGET_FIELD).gt("s0")).should(rangeQuery(TARGET_FIELD).lt("s0"));

    when(cqlSearchQueryConverter.convert(query, AUTHORITY_RESOURCE)).thenReturn(searchSource(esQuery));
    mockMultiSearchRequest(request,
      List.of(searchSource("s0", 2, DESC), searchSource("s0", 3, ASC)),
      List.of(SearchResult.of(10, authorities("r2", "r1")), searchResult(authorities("s1", "s2", "s3"))));

    var actual = authorityBrowseService.browse(request);
    assertThat(actual).isEqualTo(SearchResult.of(10, List.of(
      browseItem("r1"), browseItem("r2"), authorityBrowseItem("s0", null, true), browseItem("s1"), browseItem("s2"))));
  }

  @Test
  void browse_positive_aroundWithoutHighlighting() {
    var query = "headingRef > s0 or headingRef < s0";
    var request = BrowseRequest.of(AUTHORITY_RESOURCE, TENANT_ID, query, 5, TARGET_FIELD, true, false, 2);
    var esQuery = boolQuery().should(rangeQuery(TARGET_FIELD).gt("s0")).should(rangeQuery(TARGET_FIELD).lt("s0"));

    when(cqlSearchQueryConverter.convert(query, AUTHORITY_RESOURCE)).thenReturn(searchSource(esQuery));
    mockMultiSearchRequest(request,
      List.of(searchSource("s0", 2, DESC), searchSource("s0", 3, ASC)),
      List.of(SearchResult.of(10, authorities("r2", "r1")), searchResult(authorities("s1", "s2", "s3"))));

    var actual = authorityBrowseService.browse(request);
    assertThat(actual).isEqualTo(SearchResult.of(10, List.of(
      browseItem("r1"), browseItem("r2"), browseItem("s1"), browseItem("s2"), browseItem("s3"))));
  }

  @Test
  void browse_positive_aroundIncluding() {
    var query = "headingRef < s0 or headingRef >= s0";
    var request = BrowseRequest.of(AUTHORITY_RESOURCE, TENANT_ID, query, 5, TARGET_FIELD, true, true, 2);
    var esQuery = boolQuery().should(rangeQuery(TARGET_FIELD).lt("s0")).should(rangeQuery(TARGET_FIELD).gte("s0"));

    when(cqlSearchQueryConverter.convert(query, AUTHORITY_RESOURCE)).thenReturn(searchSource(esQuery));
    mockMultiSearchRequest(request,
      List.of(searchSource("s0", 2, DESC), searchSource("s0", 3, ASC), anchorSearchSource("s0")),
      List.of(
        SearchResult.of(10, authorities("r2", "r1")),
        searchResult(authorities("s1", "s2", "s3")),
        searchResult(authority("s0"))));

    var actual = authorityBrowseService.browse(request);
    assertThat(actual).isEqualTo(SearchResult.of(10, List.of(
      browseItem("r1"), browseItem("r2"), browseItem("s0").isAnchor(true), browseItem("s1"), browseItem("s2"))));
  }

  @Test
  void browse_positive_aroundIncludingWithoutHighlighting() {
    var query = "headingRef < s0 or headingRef >= s0";
    var request = BrowseRequest.of(AUTHORITY_RESOURCE, TENANT_ID, query, 5, TARGET_FIELD, true, false, 2);
    var esQuery = boolQuery().should(rangeQuery(TARGET_FIELD).lt("s0")).should(rangeQuery(TARGET_FIELD).gte("s0"));

    when(cqlSearchQueryConverter.convert(query, AUTHORITY_RESOURCE)).thenReturn(searchSource(esQuery));
    mockMultiSearchRequest(request,
      List.of(searchSource("s0", 2, DESC), searchSource("s0", 3, ASC), anchorSearchSource("s0")),
      List.of(
        SearchResult.of(10, authorities("r2", "r1")),
        searchResult(authorities("s1", "s2", "s3")),
        searchResult(authority("s0"))));

    var actual = authorityBrowseService.browse(request);
    assertThat(actual).isEqualTo(SearchResult.of(10, List.of(
      browseItem("r1"), browseItem("r2"), browseItem("s0"), browseItem("s1"), browseItem("s2"))));
  }

  @Test
  void browse_positive_aroundIncludingMissingAnchor() {
    var query = "headingRef <= s0 or headingRef > s0";
    var request = BrowseRequest.of(AUTHORITY_RESOURCE, TENANT_ID, query, 5, TARGET_FIELD, true, true, 2);
    var esQuery = boolQuery().should(rangeQuery(TARGET_FIELD).lte("s0")).should(rangeQuery(TARGET_FIELD).gt("s0"));

    when(cqlSearchQueryConverter.convert(query, AUTHORITY_RESOURCE)).thenReturn(searchSource(esQuery));
    mockMultiSearchRequest(request,
      List.of(searchSource("s0", 2, DESC), searchSource("s0", 3, ASC), anchorSearchSource("s0")),
      List.of(
        SearchResult.of(10, authorities("r2", "r1")),
        searchResult(authorities("s1", "s2", "s3")),
        SearchResult.empty()));

    var actual = authorityBrowseService.browse(request);
    assertThat(actual).isEqualTo(SearchResult.of(10, List.of(
      browseItem("r1"), browseItem("r2"), authorityBrowseItem("s0", null, true), browseItem("s1"), browseItem("s2"))));
  }

  @Test
  void browse_positive_aroundIncludingMissingAnchorWithoutHighlighting() {
    var query = "headingRef <= s0 or headingRef > s0";
    var request = BrowseRequest.of(AUTHORITY_RESOURCE, TENANT_ID, query, 5, TARGET_FIELD, true, false, 2);
    var esQuery = boolQuery().should(rangeQuery(TARGET_FIELD).lte("s0")).should(rangeQuery(TARGET_FIELD).gt("s0"));

    when(cqlSearchQueryConverter.convert(query, AUTHORITY_RESOURCE)).thenReturn(searchSource(esQuery));
    mockMultiSearchRequest(request,
      List.of(searchSource("s0", 2, DESC), searchSource("s0", 3, ASC), anchorSearchSource("s0")),
      List.of(
        SearchResult.of(10, authorities("r2", "r1")),
        searchResult(authorities("s1", "s2", "s3")),
        SearchResult.empty()));

    var actual = authorityBrowseService.browse(request);
    assertThat(actual).isEqualTo(SearchResult.of(10, List.of(
      browseItem("r1"), browseItem("r2"), browseItem("s1"), browseItem("s2"), browseItem("s3"))));
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

  private static SearchSourceBuilder anchorSearchSource(String headingRef) {
    var query = boolQuery()
      .filter(termsQuery("authRefType", "Authorized", "Reference"))
      .must(termQuery(TARGET_FIELD, headingRef));
    return searchSource(query).from(0).size(1).fetchSource((String[]) null, null);
  }

  private void mockMultiSearchRequest(ResourceRequest request,
    List<SearchSourceBuilder> queries, List<SearchResult<Authority>> results) {
    var multiSearchResponse = mock(MultiSearchResponse.class);
    var items = new MultiSearchResponse.Item[results.size()];
    for (int i = 0; i < results.size(); i++) {
      items[i] = mock(MultiSearchResponse.Item.class);
      var searchResponse = mock(SearchResponse.class);
      when(items[i].getResponse()).thenReturn(searchResponse);
      when(documentConverter.convertToSearchResult(searchResponse, Authority.class)).thenReturn(results.get(i));
    }

    when(searchRepository.msearch(request, queries)).thenReturn(multiSearchResponse);
    when(multiSearchResponse.getResponses()).thenReturn(items);
  }

  private static List<Authority> authorities(String... values) {
    return Arrays.stream(values).map(AuthorityBrowseServiceTest::authority).collect(Collectors.toList());
  }

  private static Authority authority(String heading) {
    return new Authority().headingRef(heading);
  }

  private static AuthorityBrowseItem browseItem(String heading) {
    return new AuthorityBrowseItem().authority(authority(heading)).headingRef(heading);
  }
}