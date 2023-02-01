package org.folio.search.service.browse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.TestConstants.TENANT_ID;
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
import java.util.stream.Collectors;
import org.folio.search.model.BrowseResult;
import org.folio.search.model.ResourceRequest;
import org.folio.search.model.SearchResult;
import org.folio.search.model.index.SubjectResource;
import org.folio.search.model.service.BrowseContext;
import org.folio.search.model.service.BrowseRequest;
import org.folio.search.repository.SearchRepository;
import org.folio.search.service.converter.ElasticsearchDocumentConverter;
import org.folio.spring.test.type.UnitTest;
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
  private static final String TARGET_FIELD = "value";

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

  public SubjectResource[] browseItems(String... subject) {
    return Arrays.stream(subject).map(sub -> {
      var subjectResource = new SubjectResource();
      subjectResource.setValue(sub);
      subjectResource.setInstances(sub.chars().mapToObj(String::valueOf).collect(Collectors.toSet()));
      return subjectResource;
    }).toArray(SubjectResource[]::new);
  }

  @Test
  void browse_positive_forward() {
    var query = "value > s0";
    var request = BrowseRequest.of(INSTANCE_SUBJECT, TENANT_ID, query, 5, TARGET_FIELD, null, null, false, 5);
    var esQuery = rangeQuery(TARGET_FIELD).gt("s0");
    var context = BrowseContext.builder().succeedingQuery(esQuery).succeedingLimit(5).anchor("s0").build();
    var expectedSearchSource = searchSource("s0", 6, ASC);

    when(browseContextProvider.get(request)).thenReturn(context);
    when(searchRepository.search(request, expectedSearchSource)).thenReturn(searchResponse);
    when(documentConverter.convertToSearchResult(searchResponse, SubjectResource.class)).thenReturn(
      searchResult(browseItems("s1", "s12", "s123", "s1234", "s12345", "s123456")));

    var browseSearchResult = subjectBrowseService.browse(request);

    assertThat(browseSearchResult).isEqualTo(BrowseResult.of(6, "s1", "s12345", List.of(
      subjectBrowseItem(2, "s1"), subjectBrowseItem(3, "s12"),
      subjectBrowseItem(4, "s123"), subjectBrowseItem(5, "s1234"),
      subjectBrowseItem(6, "s12345"))));
  }

  @Test
  void browse_positive_emptySearchResult() {
    var query = "value > s0";
    var request = BrowseRequest.of(INSTANCE_SUBJECT, TENANT_ID, query, 5, TARGET_FIELD, null, null, false, 5);
    var esQuery = rangeQuery(TARGET_FIELD).gt("s0");
    var context = BrowseContext.builder().succeedingQuery(esQuery).succeedingLimit(5).anchor("s0").build();
    var expectedSearchSource = searchSource("s0", 6, ASC);

    when(browseContextProvider.get(request)).thenReturn(context);
    when(searchRepository.search(request, expectedSearchSource)).thenReturn(searchResponse);
    when(documentConverter.convertToSearchResult(searchResponse, SubjectResource.class))
      .thenReturn(SearchResult.empty());

    var browseSearchResult = subjectBrowseService.browse(request);

    assertThat(browseSearchResult).isEqualTo(BrowseResult.empty());
  }

  @Test
  void browse_positive_backward() {
    var query = "value < s4";
    var request = BrowseRequest.of(INSTANCE_SUBJECT, TENANT_ID, query, 3, TARGET_FIELD, null, null, false, 3);
    var esQuery = rangeQuery(TARGET_FIELD).lt("s4");
    var context = BrowseContext.builder().precedingQuery(esQuery).precedingLimit(3).anchor("s4").build();
    var expectedSearchSource = searchSource("s4", 4, DESC);

    when(browseContextProvider.get(request)).thenReturn(context);
    when(searchRepository.search(request, expectedSearchSource)).thenReturn(searchResponse);
    when(documentConverter.convertToSearchResult(searchResponse, SubjectResource.class)).thenReturn(searchResult(
      browseItems("s3", "s2", "s1", "r4")));

    var browseSearchResult = subjectBrowseService.browse(request);

    assertThat(browseSearchResult).isEqualTo(BrowseResult.of(4, "s1", "s3", List.of(
      subjectBrowseItem(2, "s1"), subjectBrowseItem(2, "s2"), subjectBrowseItem(2, "s3"))));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void browse_positive_forwardIncluding(boolean highlightMatch) {
    var query = TARGET_FIELD + " >= s0";
    var request = BrowseRequest.of(INSTANCE_SUBJECT, TENANT_ID, query, 5, TARGET_FIELD, null, null, highlightMatch, 5);
    var esQuery = rangeQuery(TARGET_FIELD).gte("s0");
    var context = BrowseContext.builder().succeedingQuery(esQuery).succeedingLimit(5).anchor("s0").build();

    when(browseContextProvider.get(request)).thenReturn(context);
    mockMultiSearchRequest(request,
      List.of(searchSource("s0", 6, ASC), subjectTermQuery("s0")),
      List.of(searchResult(browseItems("s1", "s12", "s123", "s1234", "s12345")),
        searchResult(browseItems("s0"))));

    var browseSearchResult = subjectBrowseService.browse(request);

    assertThat(browseSearchResult).isEqualTo(BrowseResult.of(5, "s0", "s1234", List.of(
      subjectBrowseItem(2, "s0"), subjectBrowseItem(2, "s1"), subjectBrowseItem(3, "s12"),
      subjectBrowseItem(4, "s123"), subjectBrowseItem(5, "s1234"))));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void browse_positive_forwardIncludingAnchorNotFound(boolean highlightMatch) {
    var query = TARGET_FIELD + " >= s0";
    var request = BrowseRequest.of(INSTANCE_SUBJECT, TENANT_ID, query, 5, TARGET_FIELD, null, null, highlightMatch, 5);
    var esQuery = rangeQuery(TARGET_FIELD).gte("s0");
    var context = BrowseContext.builder().succeedingQuery(esQuery).succeedingLimit(5).anchor("s0").build();

    when(browseContextProvider.get(request)).thenReturn(context);
    mockMultiSearchRequest(request,
      List.of(searchSource("s0", 6, ASC), subjectTermQuery("s0")),
      List.of(searchResult(browseItems("s1", "s12", "s123", "s1234", "s12345")), SearchResult.empty()));

    var browseSearchResult = subjectBrowseService.browse(request);

    assertThat(browseSearchResult).isEqualTo(BrowseResult.of(5, "s1", null, List.of(
      subjectBrowseItem(2, "s1"), subjectBrowseItem(3, "s12"), subjectBrowseItem(4, "s123"),
      subjectBrowseItem(5, "s1234"), subjectBrowseItem(6, "s12345"))));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void browse_positive_backwardIncluding(boolean highlightMatch) {
    var query = TARGET_FIELD + " <= s4";
    var request = BrowseRequest.of(INSTANCE_SUBJECT, TENANT_ID, query, 3, TARGET_FIELD, null, null, highlightMatch, 3);
    var browsingSearchResult = searchResult(browseItems("s3", "s2", "s1", "s0"));
    var esQuery = rangeQuery(TARGET_FIELD).lte("s4");
    var context = BrowseContext.builder().precedingQuery(esQuery).precedingLimit(3).anchor("s4").build();

    when(browseContextProvider.get(request)).thenReturn(context);
    mockMultiSearchRequest(request,
      List.of(searchSource("s4", 4, DESC), subjectTermQuery("s4")),
      List.of(browsingSearchResult, searchResult(browseItems("s4"))));

    var browseSearchResult = subjectBrowseService.browse(request);

    assertThat(browseSearchResult).isEqualTo(BrowseResult.of(4, "s2", "s4", List.of(
      subjectBrowseItem(2, "s2"), subjectBrowseItem(2, "s3"), subjectBrowseItem(2, "s4"))));
  }

  @Test
  void browse_positive_forwardIncludingTermMissing() {
    var query = TARGET_FIELD + " >= s0";
    var request = BrowseRequest.of(INSTANCE_SUBJECT, TENANT_ID, query, 5, TARGET_FIELD, null, null, false, 5);
    var esQuery = rangeQuery(TARGET_FIELD).gte("s0");
    var context = BrowseContext.builder().succeedingQuery(esQuery).succeedingLimit(5).anchor("s0").build();
    var browsingSearchResult = searchResult(browseItems("s1", "s2"));

    when(browseContextProvider.get(request)).thenReturn(context);
    mockMultiSearchRequest(request,
      List.of(searchSource("s0", 6, ASC), subjectTermQuery("s0")),
      List.of(browsingSearchResult, SearchResult.empty()));

    var actual = subjectBrowseService.browse(request);

    assertThat(actual).isEqualTo(BrowseResult.of(2, "s1", null, List.of(
      subjectBrowseItem(2, "s1"), subjectBrowseItem(2, "s2"))));
  }

  @Test
  void browse_positive_around() {
    var query = TARGET_FIELD + " > s0 or " + TARGET_FIELD + " < s0";
    var request = BrowseRequest.of(INSTANCE_SUBJECT, TENANT_ID, query, 5, TARGET_FIELD, null, null, true, 2);

    when(browseContextProvider.get(request)).thenReturn(browseContextAround(false));
    mockMultiSearchRequest(request, List.of(searchSource("s0", 3, DESC), searchSource("s0", 4, ASC)), List.of(
      searchResult(10, browseItems("r2", "r1", "r0")),
      searchResult(10, browseItems("s1", "s2", "s3", "s4"))));

    var actual = subjectBrowseService.browse(request);
    assertThat(actual).isEqualTo(BrowseResult.of(10, "r1", "s2", List.of(
      subjectBrowseItem(2, "r1"), subjectBrowseItem(2, "r2"),
      subjectBrowseItem(0, "s0", true), subjectBrowseItem(2, "s1"), subjectBrowseItem(2, "s2"))));
  }

  @Test
  void browse_positive_aroundWhenPrevAndNextValuesMissing() {
    var query = TARGET_FIELD + " > s0 or " + TARGET_FIELD + " < s0";
    var request = BrowseRequest.of(INSTANCE_SUBJECT, TENANT_ID, query, 5, TARGET_FIELD, null, null, true, 2);

    when(browseContextProvider.get(request)).thenReturn(browseContextAround(false));
    mockMultiSearchRequest(request, List.of(searchSource("s0", 3, DESC), searchSource("s0", 4, ASC)), List.of(
      searchResult(10, browseItems("r2", "r1")),
      searchResult(browseItems("s1", "s2"))));

    var actual = subjectBrowseService.browse(request);
    assertThat(actual).isEqualTo(BrowseResult.of(10, null, null, List.of(
      subjectBrowseItem(2, "r1"), subjectBrowseItem(2, "r2"),
      subjectBrowseItem(0, "s0", true), subjectBrowseItem(2, "s1"), subjectBrowseItem(2, "s2"))));
  }

  @Test
  void browse_positive_aroundWithoutHighlighting() {
    var query = TARGET_FIELD + " > s0 or " + TARGET_FIELD + " < s0";
    var request = BrowseRequest.of(INSTANCE_SUBJECT, TENANT_ID, query, 5, TARGET_FIELD, null, null, false, 2);

    when(browseContextProvider.get(request)).thenReturn(browseContextAround(false));
    mockMultiSearchRequest(request, List.of(searchSource("s0", 3, DESC), searchSource("s0", 4, ASC)),
      List.of(searchResult(10, browseItems("r2", "r1")), searchResult(10, browseItems("s1", "s2", "s3"))));

    var actual = subjectBrowseService.browse(request);
    assertThat(actual).isEqualTo(BrowseResult.of(10, null, null, List.of(
      subjectBrowseItem(2, "r1"), subjectBrowseItem(2, "r2"),
      subjectBrowseItem(2, "s1"), subjectBrowseItem(2, "s2"), subjectBrowseItem(2, "s3"))));
  }

  @Test
  void browse_positive_aroundIncluding() {
    var query = TARGET_FIELD + " < s0 or " + TARGET_FIELD + " >= s0";
    var request = BrowseRequest.of(INSTANCE_SUBJECT, TENANT_ID, query, 5, TARGET_FIELD, null, null, true, 2);

    when(browseContextProvider.get(request)).thenReturn(browseContextAround(true));
    mockMultiSearchRequest(request,
      List.of(searchSource("s0", 3, DESC), searchSource("s0", 4, ASC), subjectTermQuery("s0")),
      List.of(
        searchResult(10, browseItems("r2", "r1", "r0")),
        searchResult(10, browseItems("s1", "s2", "s3")),
        searchResult(browseItems("s0"))));

    var actual = subjectBrowseService.browse(request);
    assertThat(actual).isEqualTo(BrowseResult.of(10, "r1", "s2", List.of(
      subjectBrowseItem(2, "r1"), subjectBrowseItem(2, "r2"), subjectBrowseItem(2, "s0", true),
      subjectBrowseItem(2, "s1"), subjectBrowseItem(2, "s2"))));
  }

  @Test
  void browse_positive_aroundIncludingWithoutHighlighting() {
    var query = TARGET_FIELD + " < s0 or " + TARGET_FIELD + " >= s0";
    var request = BrowseRequest.of(INSTANCE_SUBJECT, TENANT_ID, query, 5, TARGET_FIELD, null, null, false, 2);

    when(browseContextProvider.get(request)).thenReturn(browseContextAround(true));
    mockMultiSearchRequest(request,
      List.of(searchSource("s0", 3, DESC), searchSource("s0", 4, ASC), subjectTermQuery("s0")),
      List.of(
        SearchResult.of(10, List.of(browseItems("r2", "r1"))),
        searchResult(browseItems("s1", "s2", "s3")),
        searchResult(browseItems("s0"))));

    var actual = subjectBrowseService.browse(request);
    assertThat(actual).isEqualTo(BrowseResult.of(10, null, "s2", List.of(
      subjectBrowseItem(2, "r1"), subjectBrowseItem(2, "r2"), subjectBrowseItem(2, "s0"),
      subjectBrowseItem(2, "s1"), subjectBrowseItem(2, "s2"))));
  }

  @Test
  void browse_positive_aroundIncludingMissingAnchor() {
    var query = TARGET_FIELD + " <= s0 or " + TARGET_FIELD + " > s0";
    var request = BrowseRequest.of(INSTANCE_SUBJECT, TENANT_ID, query, 5, TARGET_FIELD, null, null, true, 2);

    when(browseContextProvider.get(request)).thenReturn(browseContextAround(true));
    mockMultiSearchRequest(request,
      List.of(searchSource("s0", 3, DESC), searchSource("s0", 4, ASC), subjectTermQuery("s0")),
      List.of(
        SearchResult.of(10, List.of(browseItems("r2", "r1", "r0"))),
        searchResult(browseItems("s1", "s2", "s3", "s4")),
        SearchResult.empty()));

    var actual = subjectBrowseService.browse(request);
    assertThat(actual).isEqualTo(BrowseResult.of(10, "r1", "s2", List.of(
      subjectBrowseItem(2, "r1"), subjectBrowseItem(2, "r2"), subjectBrowseItem(0, "s0", true),
      subjectBrowseItem(2, "s1"), subjectBrowseItem(2, "s2"))));
  }

  @Test
  void browse_positive_aroundIncludingMissingAnchorWithoutHighlighting() {
    var query = TARGET_FIELD + " <= s0 or " + TARGET_FIELD + " > s0";
    var request = BrowseRequest.of(INSTANCE_SUBJECT, TENANT_ID, query, 5, TARGET_FIELD, null, null, false, 2);

    when(browseContextProvider.get(request)).thenReturn(browseContextAround(true));
    mockMultiSearchRequest(request,
      List.of(searchSource("s0", 3, DESC), searchSource("s0", 4, ASC), subjectTermQuery("s0")),
      List.of(
        SearchResult.of(10, List.of(browseItems("r2", "r1", "r0"))),
        searchResult(browseItems("s1", "s2", "s3", "s4")),
        SearchResult.empty()));

    var actual = subjectBrowseService.browse(request);
    assertThat(actual).isEqualTo(BrowseResult.of(10, "r1", "s3", List.of(
      subjectBrowseItem(2, "r1"), subjectBrowseItem(2, "r2"), subjectBrowseItem(2, "s1"),
      subjectBrowseItem(2, "s2"), subjectBrowseItem(2, "s3"))));
  }

  private SearchSourceBuilder searchSource(String subject, int size, SortOrder sortOrder) {
    return SearchSourceBuilder.searchSource()
      .query(matchAllQuery())
      .searchAfter(new String[] {subject}).from(0).size(size)
      .sort(fieldSort(TARGET_FIELD).order(sortOrder));
  }

  private SearchSourceBuilder searchSource(QueryBuilder query) {
    return SearchSourceBuilder.searchSource().query(query);
  }

  private SearchSourceBuilder subjectTermQuery(String subjectValue) {
    return searchSource(termQuery(TARGET_FIELD, subjectValue)).from(0).size(1);
  }

  private BrowseContext browseContextAround(boolean includeAnchor) {
    var precedingQuery = rangeQuery(TARGET_FIELD).lt("s0");
    var succeedingQuery = includeAnchor ? rangeQuery(TARGET_FIELD).gte("s0") : rangeQuery(TARGET_FIELD).gt("s0");
    return BrowseContext.builder().precedingQuery(precedingQuery).precedingLimit(2)
      .succeedingQuery(succeedingQuery).succeedingLimit(3).anchor("s0").build();
  }

  private void mockMultiSearchRequest(ResourceRequest request,
                                      List<SearchSourceBuilder> queries,
                                      List<SearchResult<SubjectResource>> results) {
    var multiSearchResponse = mock(MultiSearchResponse.class);
    var items = new MultiSearchResponse.Item[results.size()];
    for (int i = 0; i < results.size(); i++) {
      items[i] = mock(MultiSearchResponse.Item.class);
      var searchResponse = mock(SearchResponse.class);
      when(items[i].getResponse()).thenReturn(searchResponse);
      when(documentConverter.convertToSearchResult(searchResponse, SubjectResource.class)).thenReturn(results.get(i));
    }

    when(searchRepository.msearch(request, queries)).thenReturn(multiSearchResponse);
    when(multiSearchResponse.getResponses()).thenReturn(items);
  }
}
