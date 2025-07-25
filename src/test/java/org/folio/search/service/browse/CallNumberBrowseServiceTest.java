package org.folio.search.service.browse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.domain.dto.BrowseOptionType.ALL;
import static org.folio.search.model.types.ResourceType.INSTANCE_CALL_NUMBER;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.utils.TestUtils.searchResult;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.opensearch.index.query.QueryBuilders.matchAllQuery;
import static org.opensearch.index.query.QueryBuilders.rangeQuery;
import static org.opensearch.search.sort.SortBuilders.fieldSort;
import static org.opensearch.search.sort.SortOrder.ASC;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.folio.search.domain.dto.BrowseConfig;
import org.folio.search.domain.dto.CallNumberBrowseItem;
import org.folio.search.domain.dto.ShelvingOrderAlgorithmType;
import org.folio.search.model.BrowseResult;
import org.folio.search.model.index.CallNumberResource;
import org.folio.search.model.index.InstanceSubResource;
import org.folio.search.model.service.BrowseContext;
import org.folio.search.model.service.BrowseRequest;
import org.folio.search.repository.SearchRepository;
import org.folio.search.service.consortium.BrowseConfigServiceDecorator;
import org.folio.search.service.consortium.ConsortiumSearchHelper;
import org.folio.search.service.converter.ElasticsearchDocumentConverter;
import org.folio.search.service.setter.SearchResponsePostProcessor;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.SortOrder;

@UnitTest
@ExtendWith(MockitoExtension.class)
class CallNumberBrowseServiceTest {

  private static final String TARGET_FIELD = "value";

  @InjectMocks
  private CallNumberBrowseService callNumberBrowseService;
  @Mock
  private SearchRepository searchRepository;
  @Mock
  private BrowseContextProvider browseContextProvider;
  @Mock
  private ElasticsearchDocumentConverter documentConverter;
  @Mock
  private ConsortiumSearchHelper consortiumSearchHelper;
  @Mock
  private SearchResponse searchResponse;
  @Mock
  private BrowseConfigServiceDecorator configServiceDecorator;
  @Mock
  private Map<Class<?>, SearchResponsePostProcessor<?>> searchResponsePostProcessors = Collections.emptyMap();

  @BeforeEach
  void setUpMocks() {
    callNumberBrowseService.setBrowseContextProvider(browseContextProvider);
    callNumberBrowseService.setDocumentConverter(documentConverter);
    callNumberBrowseService.setSearchRepository(searchRepository);
    callNumberBrowseService.setSearchResponsePostProcessors(searchResponsePostProcessors);
    doAnswer(invocation -> invocation.getArgument(1))
      .when(consortiumSearchHelper).filterBrowseQueryForActiveAffiliation(any(), any(), any());
    lenient().doAnswer(invocation -> invocation.<CallNumberResource>getArgument(1).instances())
      .when(consortiumSearchHelper).filterSubResourcesForConsortium(any(), any(), any());
    lenient().when(searchRepository.analyze(any(), any(), any(), any()))
      .thenAnswer(invocation -> invocation.getArgument(0));
    lenient().when(configServiceDecorator.getConfig(any(), any()))
      .thenReturn(new BrowseConfig().shelvingAlgorithm(ShelvingOrderAlgorithmType.DEFAULT));
  }

  @Test
  void browse_positive_forward() {
    var query = "value > s0";
    var request = BrowseRequest.of(INSTANCE_CALL_NUMBER, TENANT_ID, ALL, query, 5, TARGET_FIELD, null, "", false, 5);
    var esQuery = rangeQuery(TARGET_FIELD).gt("s0");
    var context = BrowseContext.builder().succeedingQuery(esQuery).succeedingLimit(5).anchor("s0").build();
    var expectedSearchSource = searchSource("s0", 6, ASC);

    when(browseContextProvider.get(request)).thenReturn(context);
    when(searchRepository.search(request, expectedSearchSource)).thenReturn(searchResponse);
    when(documentConverter.convertToSearchResult(searchResponse, CallNumberResource.class)).thenReturn(
      searchResult(browseItems("s1", "s12", "s123", "s1234", "s12345", "s123456")));

    var browseSearchResult = callNumberBrowseService.browse(request);

    assertThat(browseSearchResult).isEqualTo(BrowseResult.of(6, "s1", "s12345", List.of(
      callNumberBrowseItem(1, "s1", "title"),
      callNumberBrowseItem(2, "s12"),
      callNumberBrowseItem(3, "s123"),
      callNumberBrowseItem(4, "s1234"),
      callNumberBrowseItem(5, "s12345")
    )));
  }

  private CallNumberBrowseItem callNumberBrowseItem(int totalRecords, String cn, String title) {
    return new CallNumberBrowseItem().fullCallNumber(cn).callNumber(cn).instanceTitle(title).totalRecords(totalRecords);
  }

  private CallNumberBrowseItem callNumberBrowseItem(int totalRecords, String cn) {
    return callNumberBrowseItem(totalRecords, cn, null);
  }

  private CallNumberResource[] browseItems(String... subject) {
    return Arrays.stream(subject)
      .map(sub -> new CallNumberResource("id", sub, sub, null, null, null, buildSubResources(sub)))
      .toArray(CallNumberResource[]::new);
  }

  private Set<InstanceSubResource> buildSubResources(String sub) {
    var instanceIds1 = IntStream.range(1, sub.length())
      .mapToObj(String::valueOf)
      .toList();
    var instanceIds2 = IntStream.range(1, sub.length() - 1)
      .mapToObj(String::valueOf)
      .toList();
    return Set.of(
      InstanceSubResource.builder().tenantId(TENANT_ID).instanceId(instanceIds1).instanceTitle("title").build(),
      InstanceSubResource.builder().tenantId(TENANT_ID).instanceId(instanceIds2).instanceTitle("title").build()
    );
  }

  private SearchSourceBuilder searchSource(String subject, int size, SortOrder sortOrder) {
    return SearchSourceBuilder.searchSource()
      .query(matchAllQuery())
      .searchAfter(new String[] {subject, subject}).from(0).size(size)
      .sort(fieldSort("defaultShelvingOrder").order(sortOrder))
      .sort(fieldSort("value").order(sortOrder));
  }
}
