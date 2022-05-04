package org.folio.search.service.browse;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.script.Script.DEFAULT_SCRIPT_LANG;
import static org.elasticsearch.script.ScriptType.INLINE;
import static org.elasticsearch.search.builder.SearchSourceBuilder.searchSource;
import static org.elasticsearch.search.sort.ScriptSortBuilder.ScriptSortType.STRING;
import static org.elasticsearch.search.sort.SortBuilders.scriptSort;
import static org.elasticsearch.search.sort.SortOrder.ASC;
import static org.elasticsearch.search.sort.SortOrder.DESC;
import static org.folio.search.model.types.ResponseGroupType.CN_BROWSE;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.folio.search.configuration.properties.SearchQueryConfigurationProperties;
import org.folio.search.model.service.BrowseContext;
import org.folio.search.model.service.BrowseRequest;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class CallNumberBrowseQueryProviderTest {

  private static final String ANCHOR = "A";
  private static final String RANGE_FIELD = "callNumber";
  private static final long ANCHOR_AS_NUMBER = 200L;

  @InjectMocks private CallNumberBrowseQueryProvider queryProvider;
  @Mock private SearchFieldProvider searchFieldProvider;
  @Mock private CallNumberTermConverter callNumberTermConverter;
  @Mock private CallNumberBrowseRangeService browseRangeService;
  @Spy private final SearchQueryConfigurationProperties queryConfiguration = getSearchQueryConfigurationProperties();

  @Test
  void get_positive_forward() {
    when(callNumberTermConverter.convert(ANCHOR)).thenReturn(ANCHOR_AS_NUMBER);
    when(searchFieldProvider.getSourceFields(RESOURCE_NAME, CN_BROWSE)).thenReturn(new String[] {"id", "title"});
    var context = BrowseContext.builder().anchor(ANCHOR).succeedingLimit(5).build();

    var actual = queryProvider.get(request(false), context, true);

    assertThat(actual).isEqualTo(expectedSucceedingQuery(25).fetchSource(new String[] {"id", "title"}, null));
    verify(queryConfiguration).getRangeQueryLimitMultiplier();
  }

  @Test
  void get_positive_forwardQueryWithFilters() {
    when(callNumberTermConverter.convert(ANCHOR)).thenReturn(ANCHOR_AS_NUMBER);
    when(searchFieldProvider.getSourceFields(RESOURCE_NAME, CN_BROWSE)).thenReturn(new String[] {"id", "title"});
    var filterQuery = termQuery("effectiveLocationId", "location#1");
    var context = BrowseContext.builder().anchor(ANCHOR).succeedingLimit(5)
      .filters(List.of(filterQuery)).build();

    var actual = queryProvider.get(request(false), context, true);

    var source = expectedSucceedingQuery(25);
    source.query(boolQuery().must(source.query()).filter(filterQuery));
    assertThat(actual).isEqualTo(source.fetchSource(new String[] {"id", "title"}, null));
    verify(queryConfiguration).getRangeQueryLimitMultiplier();
  }

  @Test
  void get_positive_forwardWithExpandAll() {
    when(callNumberTermConverter.convert(ANCHOR)).thenReturn(ANCHOR_AS_NUMBER);
    var context = BrowseContext.builder().anchor(ANCHOR).succeedingLimit(20).build();

    var actual = queryProvider.get(request(true), context, true);

    assertThat(actual).isEqualTo(expectedSucceedingQuery(60));
    verify(queryConfiguration).getRangeQueryLimitMultiplier();
  }

  @Test
  void get_positive_forwardWithEnabledOptimization() {
    var size = 25;
    when(queryConfiguration.isCallNumberBrowseOptimizationEnabled()).thenReturn(true);
    when(callNumberTermConverter.convert(ANCHOR)).thenReturn(ANCHOR_AS_NUMBER);
    when(browseRangeService.getRangeBoundaryForBrowsing(TENANT_ID, ANCHOR, size, true)).thenReturn(Optional.of(100L));

    var context = BrowseContext.builder().anchor(ANCHOR).succeedingLimit(5).build();
    var actual = queryProvider.get(request(true), context, true);

    var expectedRangeQuery = rangeQuery(RANGE_FIELD).gte(ANCHOR_AS_NUMBER).lte(100L);
    assertThat(actual).isEqualTo(expectedSucceedingQuery(size, expectedRangeQuery));
    verify(queryConfiguration).getRangeQueryLimitMultiplier();
  }

  @Test
  void get_positive_backward() {
    when(callNumberTermConverter.convert(ANCHOR)).thenReturn(ANCHOR_AS_NUMBER);
    when(searchFieldProvider.getSourceFields(RESOURCE_NAME, CN_BROWSE)).thenReturn(new String[] {"id", "title"});
    var context = BrowseContext.builder().anchor(ANCHOR).precedingLimit(5).build();

    var actual = queryProvider.get(request(false), context, false);

    assertThat(actual).isEqualTo(expectedPrecedingQuery(25).fetchSource(new String[] {"id", "title"}, null));
    verify(queryConfiguration).getRangeQueryLimitMultiplier();
  }

  @Test
  void get_positive_backwardWithExpandAll() {
    when(callNumberTermConverter.convert(ANCHOR)).thenReturn(ANCHOR_AS_NUMBER);
    var context = BrowseContext.builder().anchor(ANCHOR).precedingLimit(20).build();

    var actual = queryProvider.get(request(true), context, false);

    assertThat(actual).isEqualTo(expectedPrecedingQuery(60));
    verify(queryConfiguration).getRangeQueryLimitMultiplier();
  }

  @Test
  void get_positive_backwardWithEnabledOptimization() {
    var size = 25;
    when(queryConfiguration.isCallNumberBrowseOptimizationEnabled()).thenReturn(true);
    when(callNumberTermConverter.convert(ANCHOR)).thenReturn(ANCHOR_AS_NUMBER);
    when(browseRangeService.getRangeBoundaryForBrowsing(TENANT_ID, ANCHOR, size, false)).thenReturn(Optional.of(100L));

    var context = BrowseContext.builder().anchor(ANCHOR).precedingLimit(5).build();
    var actual = queryProvider.get(request(true), context, false);

    var expectedRangeQuery = rangeQuery(RANGE_FIELD).lte(ANCHOR_AS_NUMBER).gte(100L);
    assertThat(actual).isEqualTo(expectedPrecedingQuery(size, expectedRangeQuery));
    verify(queryConfiguration).getRangeQueryLimitMultiplier();
  }

  private static SearchSourceBuilder expectedSucceedingQuery(int size) {
    return expectedSucceedingQuery(size, rangeQuery(RANGE_FIELD).gte(ANCHOR_AS_NUMBER));
  }

  private static SearchSourceBuilder expectedSucceedingQuery(int size, QueryBuilder queryBuilder) {
    var script = new Script(INLINE, DEFAULT_SCRIPT_LANG, succeedingQuerySortScript(), singletonMap("cn", ANCHOR));
    return searchSource().from(0).size(size).query(queryBuilder).sort(scriptSort(script, STRING).order(ASC));
  }

  private static SearchSourceBuilder expectedPrecedingQuery(int size) {
    return expectedPrecedingQuery(size, rangeQuery(RANGE_FIELD).lte(ANCHOR_AS_NUMBER));
  }

  private static SearchSourceBuilder expectedPrecedingQuery(int size, QueryBuilder queryBuilder) {
    var script = new Script(INLINE, DEFAULT_SCRIPT_LANG, precedingQuerySortScript(), singletonMap("cn", ANCHOR));
    return searchSource().from(0).size(size).query(queryBuilder).sort(scriptSort(script, STRING).order(DESC));
  }

  private static BrowseRequest request(boolean expandAll) {
    return BrowseRequest.builder().resource(RESOURCE_NAME).tenantId(TENANT_ID).expandAll(expandAll).build();
  }

  private static String precedingQuerySortScript() {
    return "def f=doc['itemEffectiveShelvingOrder'];"
      + "def a=Collections.binarySearch(f,params['cn']);"
      + "if(a>=0) return f[a];a=-a-2"
      + ";f[(int)Math.min(Math.max(0, a),f.length-1)]";
  }

  private static String succeedingQuerySortScript() {
    return "def f=doc['itemEffectiveShelvingOrder'];"
      + "def a=Collections.binarySearch(f,params['cn']);"
      + "if(a>=0) return f[a];a=-a-1"
      + ";f[(int)Math.min(Math.max(0, a),f.length-1)]";
  }

  private static SearchQueryConfigurationProperties getSearchQueryConfigurationProperties() {
    var config = new SearchQueryConfigurationProperties();
    config.setRangeQueryLimitMultiplier(3d);
    return config;
  }
}
