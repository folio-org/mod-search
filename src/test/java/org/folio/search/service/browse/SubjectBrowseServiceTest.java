package org.folio.search.service.browse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.JsonUtils.jsonArray;
import static org.folio.search.utils.JsonUtils.jsonObject;
import static org.folio.search.utils.SearchQueryUtils.getSubjectCountsQuery;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.aggregationsFromJson;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.searchResult;
import static org.folio.search.utils.TestUtils.subjectBrowseItem;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opensearch.index.query.QueryBuilders.matchAllQuery;
import static org.opensearch.index.query.QueryBuilders.rangeQuery;
import static org.opensearch.index.query.QueryBuilders.termQuery;
import static org.opensearch.search.sort.SortBuilders.fieldSort;
import static org.opensearch.search.sort.SortOrder.ASC;
import static org.opensearch.search.sort.SortOrder.DESC;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.folio.search.domain.dto.SubjectBrowseItem;
import org.folio.search.model.BrowseResult;
import org.folio.search.model.ResourceRequest;
import org.folio.search.model.SearchResult;
import org.folio.search.model.SimpleResourceRequest;
import org.folio.search.model.service.BrowseContext;
import org.folio.search.model.service.BrowseRequest;
import org.folio.search.repository.SearchRepository;
import org.folio.search.service.converter.ElasticsearchDocumentConverter;
import org.folio.search.utils.TestUtils;
import org.folio.search.utils.types.UnitTest;
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
class SubjectBrowseServiceTest {

  private static final String INSTANCE_SUBJECT = "instance_subject";
  private static final String TARGET_FIELD = "subject";

  @InjectMocks
  private SubjectBrowseService subjectBrowseService;
  @Mock
  private SearchRepository searchRepository;
  @Mock
  private BrowseContextProvider browseContextProvider;
  @Mock
  private ElasticsearchDocumentConverter documentConverter;
  @Mock
  private SearchResponse searchResponse;

  @Test
  void browse_positive_forward() {
    var query = "subject > s0";
    var request = BrowseRequest.of(INSTANCE_SUBJECT, TENANT_ID, query, 5, TARGET_FIELD, null, false, 5);
    var esQuery = rangeQuery(TARGET_FIELD).gt("s0");
    var context = BrowseContext.builder().succeedingQuery(esQuery).succeedingLimit(5).anchor("s0").build();
    var expectedSearchSource = searchSource("s0", 6, ASC);

    when(browseContextProvider.get(request)).thenReturn(context);
    when(searchRepository.search(request, expectedSearchSource)).thenReturn(searchResponse);
    when(documentConverter.convertToSearchResult(searchResponse, SubjectBrowseItem.class)).thenReturn(
      searchResult(browseItems("s1", "s2", "s3", "s4", "s5", "s6")));
    mockCountSearchResponse(mapOf("s1", 1, "s2", 2, "s3", 3, "s4", 2, "s5", 1));

    var browseSearchResult = subjectBrowseService.browse(request);

    assertThat(browseSearchResult).isEqualTo(BrowseResult.of(6, "s1", "s5", List.of(
      subjectBrowseItem(1, "s1"), subjectBrowseItem(2, "s2"), subjectBrowseItem(3, "s3"),
      subjectBrowseItem(2, "s4"), subjectBrowseItem(1, "s5"))));
  }

  @Test
  void browse_positive_emptySearchResult() {
    var query = "subject > s0";
    var request = BrowseRequest.of(INSTANCE_SUBJECT, TENANT_ID, query, 5, TARGET_FIELD, null, false, 5);
    var esQuery = rangeQuery(TARGET_FIELD).gt("s0");
    var context = BrowseContext.builder().succeedingQuery(esQuery).succeedingLimit(5).anchor("s0").build();
    var expectedSearchSource = searchSource("s0", 6, ASC);

    when(browseContextProvider.get(request)).thenReturn(context);
    when(searchRepository.search(request, expectedSearchSource)).thenReturn(searchResponse);
    when(documentConverter.convertToSearchResult(searchResponse, SubjectBrowseItem.class))
      .thenReturn(SearchResult.empty());

    var browseSearchResult = subjectBrowseService.browse(request);

    assertThat(browseSearchResult).isEqualTo(BrowseResult.empty());
  }

  @Test
  void browse_positive_backward() {
    var query = "subject < s4";
    var request = BrowseRequest.of(INSTANCE_SUBJECT, TENANT_ID, query, 3, TARGET_FIELD, null, false, 3);
    var esQuery = rangeQuery(TARGET_FIELD).lt("s4");
    var context = BrowseContext.builder().precedingQuery(esQuery).precedingLimit(3).anchor("s4").build();
    var expectedSearchSource = searchSource("s4", 4, DESC);

    when(browseContextProvider.get(request)).thenReturn(context);
    when(searchRepository.search(request, expectedSearchSource)).thenReturn(searchResponse);
    when(documentConverter.convertToSearchResult(searchResponse, SubjectBrowseItem.class)).thenReturn(searchResult(
      browseItems("s3", "s2", "s1", "r4")));
    mockCountSearchResponse(mapOf("s1", 1, "s2", 2, "s3", 3));

    var browseSearchResult = subjectBrowseService.browse(request);

    assertThat(browseSearchResult).isEqualTo(BrowseResult.of(4, "s1", "s3", List.of(
      subjectBrowseItem(1, "s1"), subjectBrowseItem(2, "s2"), subjectBrowseItem(3, "s3"))));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void browse_positive_forwardIncluding(boolean highlightMatch) {
    var query = "subject >= s0";
    var request = BrowseRequest.of(INSTANCE_SUBJECT, TENANT_ID, query, 5, TARGET_FIELD, null, highlightMatch, 5);
    var esQuery = rangeQuery(TARGET_FIELD).gte("s0");
    var context = BrowseContext.builder().succeedingQuery(esQuery).succeedingLimit(5).anchor("s0").build();
    var browsingSearchResult = searchResult(
      subjectBrowseItem("s1"), subjectBrowseItem("s2"), subjectBrowseItem("s3"),
      subjectBrowseItem("s4"), subjectBrowseItem("s5"));

    when(browseContextProvider.get(request)).thenReturn(context);
    mockMultiSearchRequest(request,
      List.of(searchSource("s0", 6, ASC), subjectTermQuery("s0")),
      List.of(browsingSearchResult, searchResult(subjectBrowseItem("s0"))));
    mockCountSearchResponse(mapOf("s0", 1, "s1", 2, "s2", 3, "s3", 2, "s4", 1));

    var browseSearchResult = subjectBrowseService.browse(request);

    assertThat(browseSearchResult).isEqualTo(BrowseResult.of(5, "s0", "s4", List.of(
      subjectBrowseItem(1, "s0"), subjectBrowseItem(2, "s1"), subjectBrowseItem(3, "s2"),
      subjectBrowseItem(2, "s3"), subjectBrowseItem(1, "s4"))));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void browse_positive_forwardIncludingAnchorNotFound(boolean highlightMatch) {
    var query = "subject >= s0";
    var request = BrowseRequest.of(INSTANCE_SUBJECT, TENANT_ID, query, 5, TARGET_FIELD, null, highlightMatch, 5);
    var browsingSearchResult = searchResult(
      subjectBrowseItem("s1"), subjectBrowseItem("s2"), subjectBrowseItem("s3"),
      subjectBrowseItem("s4"), subjectBrowseItem("s5"));
    var esQuery = rangeQuery(TARGET_FIELD).gte("s0");
    var context = BrowseContext.builder().succeedingQuery(esQuery).succeedingLimit(5).anchor("s0").build();

    when(browseContextProvider.get(request)).thenReturn(context);
    mockMultiSearchRequest(request,
      List.of(searchSource("s0", 6, ASC), subjectTermQuery("s0")),
      List.of(browsingSearchResult, SearchResult.empty()));
    mockCountSearchResponse(mapOf("s1", 2, "s2", 3, "s3", 2, "s4", 1, "s5", 10));

    var browseSearchResult = subjectBrowseService.browse(request);

    assertThat(browseSearchResult).isEqualTo(BrowseResult.of(5, "s1", null, List.of(
      subjectBrowseItem(2, "s1"), subjectBrowseItem(3, "s2"), subjectBrowseItem(2, "s3"),
      subjectBrowseItem(1, "s4"), subjectBrowseItem(10, "s5"))));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void browse_positive_backwardIncluding(boolean highlightMatch) {
    var query = "subject <= s4";
    var request = BrowseRequest.of(INSTANCE_SUBJECT, TENANT_ID, query, 3, TARGET_FIELD, null, highlightMatch, 3);
    var browsingSearchResult = searchResult(browseItems("s3", "s2", "s1", "s0"));
    var esQuery = rangeQuery(TARGET_FIELD).lte("s4");
    var context = BrowseContext.builder().precedingQuery(esQuery).precedingLimit(3).anchor("s4").build();

    when(browseContextProvider.get(request)).thenReturn(context);
    mockMultiSearchRequest(request,
      List.of(searchSource("s4", 4, DESC), subjectTermQuery("s4")),
      List.of(browsingSearchResult, searchResult(subjectBrowseItem("s4"))));
    mockCountSearchResponse(mapOf("s2", 14, "s3", 5, "s4", 10));

    var browseSearchResult = subjectBrowseService.browse(request);

    assertThat(browseSearchResult).isEqualTo(BrowseResult.of(4, "s2", "s4", List.of(
      subjectBrowseItem(14, "s2"), subjectBrowseItem(5, "s3"), subjectBrowseItem(10, "s4"))));
  }

  @Test
  void browse_positive_forwardIncludingTermMissing() {
    var query = "subject >= s0";
    var request = BrowseRequest.of(INSTANCE_SUBJECT, TENANT_ID, query, 5, "subject", null, false, 5);
    var esQuery = rangeQuery("subject").gte("s0");
    var context = BrowseContext.builder().succeedingQuery(esQuery).succeedingLimit(5).anchor("s0").build();
    var browsingSearchResult = searchResult(browseItems("s1", "s2"));

    when(browseContextProvider.get(request)).thenReturn(context);
    mockCountSearchResponse(mapOf("s1", 1, "s2", 2));
    mockMultiSearchRequest(request,
      List.of(searchSource("s0", 6, ASC), subjectTermQuery("s0")),
      List.of(browsingSearchResult, SearchResult.empty()));

    var actual = subjectBrowseService.browse(request);

    assertThat(actual).isEqualTo(BrowseResult.of(2, "s1", null, List.of(
      subjectBrowseItem(1, "s1"), subjectBrowseItem(2, "s2"))));
  }

  @Test
  void browse_positive_around() {
    var query = "subject > s0 or subject < s0";
    var request = BrowseRequest.of(INSTANCE_SUBJECT, TENANT_ID, query, 5, TARGET_FIELD, null, true, 2);

    when(browseContextProvider.get(request)).thenReturn(browseContextAround(false));
    mockMultiSearchRequest(request, List.of(searchSource("s0", 3, DESC), searchSource("s0", 4, ASC)), List.of(
      searchResult(10, browseItems("r2", "r1", "r0")),
      searchResult(10, browseItems("s1", "s2", "s3", "s4"))));
    mockCountSearchResponse(mapOf("r1", 4, "r2", 1, "s1", 10, "s2", 5));

    var actual = subjectBrowseService.browse(request);
    assertThat(actual).isEqualTo(BrowseResult.of(10, "r1", "s2", List.of(
      subjectBrowseItem(4, "r1"), subjectBrowseItem(1, "r2"),
      subjectBrowseItem(0, "s0", true), subjectBrowseItem(10, "s1"), subjectBrowseItem(5, "s2"))));
  }

  @Test
  void browse_positive_aroundWhenPrevAndNextValuesMissing() {
    var query = "subject > s0 or subject < s0";
    var request = BrowseRequest.of(INSTANCE_SUBJECT, TENANT_ID, query, 5, TARGET_FIELD, null, true, 2);

    when(browseContextProvider.get(request)).thenReturn(browseContextAround(false));
    mockMultiSearchRequest(request, List.of(searchSource("s0", 3, DESC), searchSource("s0", 4, ASC)), List.of(
      searchResult(10, browseItems("r2", "r1")),
      searchResult(browseItems("s1", "s2"))));
    mockCountSearchResponse(mapOf("r1", 4, "r2", 1, "s1", 10, "s2", 5));

    var actual = subjectBrowseService.browse(request);
    assertThat(actual).isEqualTo(BrowseResult.of(10, null, null, List.of(
      subjectBrowseItem(4, "r1"), subjectBrowseItem(1, "r2"),
      subjectBrowseItem(0, "s0", true), subjectBrowseItem(10, "s1"), subjectBrowseItem(5, "s2"))));
  }

  @Test
  void browse_positive_aroundWithoutHighlighting() {
    var query = "subject > s0 or subject < s0";
    var request = BrowseRequest.of(INSTANCE_SUBJECT, TENANT_ID, query, 5, TARGET_FIELD, null, false, 2);

    when(browseContextProvider.get(request)).thenReturn(browseContextAround(false));
    mockMultiSearchRequest(request, List.of(searchSource("s0", 3, DESC), searchSource("s0", 4, ASC)),
      List.of(searchResult(10, browseItems("r2", "r1")), searchResult(10, browseItems("s1", "s2", "s3"))));
    mockCountSearchResponse(mapOf("r1", 4, "r2", 1, "s1", 10, "s2", 5, "s3", 12));

    var actual = subjectBrowseService.browse(request);
    assertThat(actual).isEqualTo(BrowseResult.of(10, null, null, List.of(
      subjectBrowseItem(4, "r1"), subjectBrowseItem(1, "r2"),
      subjectBrowseItem(10, "s1"), subjectBrowseItem(5, "s2"), subjectBrowseItem(12, "s3"))));
  }

  @Test
  void browse_positive_aroundIncluding() {
    var query = "subject < s0 or subject >= s0";
    var request = BrowseRequest.of(INSTANCE_SUBJECT, TENANT_ID, query, 5, TARGET_FIELD, null, true, 2);

    when(browseContextProvider.get(request)).thenReturn(browseContextAround(true));
    mockMultiSearchRequest(request,
      List.of(searchSource("s0", 3, DESC), searchSource("s0", 4, ASC), subjectTermQuery("s0")),
      List.of(
        searchResult(10, browseItems("r2", "r1", "r0")),
        searchResult(10, browseItems("s1", "s2", "s3")),
        searchResult(browseItems("s0"))));
    mockCountSearchResponse(mapOf("r1", 4, "r2", 1, "s0", 5, "s1", 10, "s2", 5));

    var actual = subjectBrowseService.browse(request);
    assertThat(actual).isEqualTo(BrowseResult.of(10, "r1", "s2", List.of(
      subjectBrowseItem(4, "r1"), subjectBrowseItem(1, "r2"), subjectBrowseItem(5, "s0", true),
      subjectBrowseItem(10, "s1"), subjectBrowseItem(5, "s2"))));
  }

  @Test
  void browse_positive_aroundIncludingWithoutHighlighting() {
    var query = "subject < s0 or subject >= s0";
    var request = BrowseRequest.of(INSTANCE_SUBJECT, TENANT_ID, query, 5, TARGET_FIELD, null, false, 2);

    when(browseContextProvider.get(request)).thenReturn(browseContextAround(true));
    mockMultiSearchRequest(request,
      List.of(searchSource("s0", 3, DESC), searchSource("s0", 4, ASC), subjectTermQuery("s0")),
      List.of(
        SearchResult.of(10, List.of(browseItems("r2", "r1"))),
        searchResult(browseItems("s1", "s2", "s3")),
        searchResult(browseItems("s0"))));
    mockCountSearchResponse(mapOf("r1", 4, "r2", 1, "s0", 5, "s1", 10, "s2", 5));

    var actual = subjectBrowseService.browse(request);
    assertThat(actual).isEqualTo(BrowseResult.of(10, null, "s2", List.of(
      subjectBrowseItem(4, "r1"), subjectBrowseItem(1, "r2"), subjectBrowseItem(5, "s0"),
      subjectBrowseItem(10, "s1"), subjectBrowseItem(5, "s2"))));
  }

  @Test
  void browse_positive_aroundIncludingMissingAnchor() {
    var query = "subject <= s0 or subject > s0";
    var request = BrowseRequest.of(INSTANCE_SUBJECT, TENANT_ID, query, 5, TARGET_FIELD, null, true, 2);

    when(browseContextProvider.get(request)).thenReturn(browseContextAround(true));
    mockMultiSearchRequest(request,
      List.of(searchSource("s0", 3, DESC), searchSource("s0", 4, ASC), subjectTermQuery("s0")),
      List.of(
        SearchResult.of(10, List.of(browseItems("r2", "r1", "r0"))),
        searchResult(browseItems("s1", "s2", "s3", "s4")),
        SearchResult.empty()));
    mockCountSearchResponse(mapOf("r1", 4, "r2", 1, "s1", 10, "s2", 5));

    var actual = subjectBrowseService.browse(request);
    assertThat(actual).isEqualTo(BrowseResult.of(10, "r1", "s2", List.of(
      subjectBrowseItem(4, "r1"), subjectBrowseItem(1, "r2"), subjectBrowseItem(0, "s0", true),
      subjectBrowseItem(10, "s1"), subjectBrowseItem(5, "s2"))));
  }

  @Test
  void browse_positive_aroundIncludingMissingAnchorWithoutHighlighting() {
    var query = "subject <= s0 or subject > s0";
    var request = BrowseRequest.of(INSTANCE_SUBJECT, TENANT_ID, query, 5, TARGET_FIELD, null, false, 2);

    when(browseContextProvider.get(request)).thenReturn(browseContextAround(true));
    mockMultiSearchRequest(request,
      List.of(searchSource("s0", 3, DESC), searchSource("s0", 4, ASC), subjectTermQuery("s0")),
      List.of(
        SearchResult.of(10, List.of(browseItems("r2", "r1", "r0"))),
        searchResult(browseItems("s1", "s2", "s3", "s4")),
        SearchResult.empty()));
    mockCountSearchResponse(mapOf("r1", 4, "r2", 1, "s1", 10, "s2", 5, "s3", 11));

    var actual = subjectBrowseService.browse(request);
    assertThat(actual).isEqualTo(BrowseResult.of(10, "r1", "s3", List.of(
      subjectBrowseItem(4, "r1"), subjectBrowseItem(1, "r2"), subjectBrowseItem(10, "s1"),
      subjectBrowseItem(5, "s2"), subjectBrowseItem(11, "s3"))));
  }

  private static SearchSourceBuilder searchSource(String subject, int size, SortOrder sortOrder) {
    return SearchSourceBuilder.searchSource()
      .query(matchAllQuery())
      .searchAfter(new String[] {subject}).from(0).size(size)
      .sort(fieldSort(TARGET_FIELD).order(sortOrder));
  }

  private static SearchSourceBuilder searchSource(QueryBuilder query) {
    return SearchSourceBuilder.searchSource().query(query);
  }

  private static SearchSourceBuilder subjectTermQuery(String subjectValue) {
    return searchSource(termQuery(TARGET_FIELD, subjectValue)).from(0).size(1);
  }

  private void mockCountSearchResponse(Map<String, Integer> expectedSubjectCounts) {
    var aggregationBuckets = jsonArray();
    expectedSubjectCounts.entrySet().stream()
      .filter(entry -> entry.getValue() != null)
      .forEach(entry -> aggregationBuckets.add(jsonObject("key", entry.getKey(), "doc_count", entry.getValue())));

    var countQueryResponse = mock(SearchResponse.class);
    var subjectsQuerySource = getSubjectCountsQuery(expectedSubjectCounts.keySet());
    var request = SimpleResourceRequest.of(INSTANCE_RESOURCE, TENANT_ID);
    when(searchRepository.search(request, subjectsQuerySource)).thenReturn(countQueryResponse);
    when(countQueryResponse.getAggregations()).thenReturn(
      aggregationsFromJson(jsonObject("sterms#subjects", jsonObject("buckets", aggregationBuckets))));
  }

  private void mockMultiSearchRequest(ResourceRequest request,
                                      List<SearchSourceBuilder> queries,
                                      List<SearchResult<SubjectBrowseItem>> results) {
    var multiSearchResponse = mock(MultiSearchResponse.class);
    var items = new MultiSearchResponse.Item[results.size()];
    for (int i = 0; i < results.size(); i++) {
      items[i] = mock(MultiSearchResponse.Item.class);
      var searchResponse = mock(SearchResponse.class);
      when(items[i].getResponse()).thenReturn(searchResponse);
      when(documentConverter.convertToSearchResult(searchResponse, SubjectBrowseItem.class)).thenReturn(results.get(i));
    }

    when(searchRepository.msearch(request, queries)).thenReturn(multiSearchResponse);
    when(multiSearchResponse.getResponses()).thenReturn(items);
  }

  private static BrowseContext browseContextAround(boolean includeAnchor) {
    var precedingQuery = rangeQuery(TARGET_FIELD).lt("s0");
    var succeedingQuery = includeAnchor ? rangeQuery(TARGET_FIELD).gte("s0") : rangeQuery(TARGET_FIELD).gt("s0");
    return BrowseContext.builder().precedingQuery(precedingQuery).precedingLimit(2)
      .succeedingQuery(succeedingQuery).succeedingLimit(3).anchor("s0").build();
  }

  public static SubjectBrowseItem[] browseItems(String... subject) {
    return Arrays.stream(subject).map(TestUtils::subjectBrowseItem).toArray(SubjectBrowseItem[]::new);
  }
}
