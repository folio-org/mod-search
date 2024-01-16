package org.folio.search.service.browse;

import static java.lang.Boolean.TRUE;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.search.client.InventoryReferenceDataClient.ReferenceDataType.CALL_NUMBER_TYPES;
import static org.folio.search.model.client.CqlQueryParam.SOURCE;
import static org.folio.search.model.types.CallNumberTypeSource.FOLIO;
import static org.folio.search.utils.CallNumberUtils.excludeIrrelevantResultItems;
import static org.folio.search.utils.CollectionUtils.mergeSafelyToList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.cql.CqlSearchQueryConverter;
import org.folio.search.cql.EffectiveShelvingOrderTermProcessor;
import org.folio.search.domain.dto.CallNumberBrowseItem;
import org.folio.search.integration.ReferenceDataService;
import org.folio.search.model.BrowseResult;
import org.folio.search.model.service.BrowseContext;
import org.folio.search.model.service.BrowseRequest;
import org.folio.search.repository.SearchRepository;
import org.folio.search.utils.CallNumberUtils;
import org.opensearch.action.search.MultiSearchResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class CallNumberBrowseService extends AbstractBrowseService<CallNumberBrowseItem> {

  public static final List<String> FOLIO_CALL_NUMBER_TYPES_SOURCES = Collections.singletonList(FOLIO.getSource());
  private static final int ADDITIONAL_REQUEST_SIZE = 100;
  private static final int MAX_ADDITIONAL_REQUEST_SIZE = 800;
  private final SearchRepository searchRepository;
  private final CqlSearchQueryConverter cqlSearchQueryConverter;
  private final CallNumberBrowseQueryProvider callNumberBrowseQueryProvider;
  private final CallNumberBrowseResultConverter callNumberBrowseResultConverter;
  private final EffectiveShelvingOrderTermProcessor effectiveShelvingOrderTermProcessor;
  private final ReferenceDataService referenceDataService;
  private final SearchConfigurationProperties searchConfig;

  @Override
  protected BrowseResult<CallNumberBrowseItem> browseInOneDirection(BrowseRequest request, BrowseContext context) {
    log.debug("browseInOneDirection:: by: [request: {}]", request);

    var callNumber = callNumberFromRequest(request);
    var initialAnchor = effectiveShelvingOrderTermProcessor.getSearchTerm(callNumber, request.getRefinedCondition());
    if (!initialAnchor.equals(context.getAnchor())) {
      context = buildBrowseContext(context, initialAnchor);
    }

    var isBrowsingForward = context.isBrowsingForward();
    var searchSource = callNumberBrowseQueryProvider.get(request, context, isBrowsingForward);
    var searchResponse = searchRepository.search(request, searchSource);

    if (isBlank(request.getRefinedCondition()) && !isAnchorPresent(searchResponse, context)) {
      var anchors = getAnchors(request);
      anchors.remove(initialAnchor);
      for (String anchor : anchors) {
        var contextForAnchor = buildBrowseContext(context, anchor);
        var searchSourceForAnchor = callNumberBrowseQueryProvider.get(request, contextForAnchor, isBrowsingForward);
        var responseForAnchor = searchRepository.search(request, searchSourceForAnchor);

        if (isAnchorPresent(searchResponse, context)) {
          context = contextForAnchor;
          searchResponse = responseForAnchor;
          break;
        }
      }
    }

    var folioCallNumberTypes = folioCallNumberTypes();
    var browseResult = callNumberBrowseResultConverter.convert(searchResponse, context, isBrowsingForward);
    var records = browseResult.getRecords();
    records = excludeIrrelevantResultItems(context, request.getRefinedCondition(), folioCallNumberTypes, records);
    return new BrowseResult<CallNumberBrowseItem>()
      .records(trim(records, context, isBrowsingForward))
      .totalRecords(browseResult.getTotalRecords())
      .prev(getPrevBrowsingValue(records, context, isBrowsingForward))
      .next(getNextBrowsingValue(records, context, isBrowsingForward));
  }

  @Override
  protected BrowseResult<CallNumberBrowseItem> browseAround(BrowseRequest request, BrowseContext context) {
    log.debug("browseAround:: by: [request: {}]", request);

    var callNumber = callNumberFromRequest(request);
    var initialAnchor = effectiveShelvingOrderTermProcessor.getSearchTerm(callNumber, request.getRefinedCondition());
    if (!initialAnchor.equals(context.getAnchor())) {
      context = buildBrowseContext(context, initialAnchor);
    }

    var precedingQuery = callNumberBrowseQueryProvider.get(request, context, false);
    var succeedingQuery = callNumberBrowseQueryProvider.get(request, context, true);
    var responses = getBrowseAround(request, precedingQuery, succeedingQuery);

    if (isBlank(request.getRefinedCondition()) && !isAnchorPresent(responses[1].getResponse(), context)) {
      var anchors = getAnchors(callNumber);
      anchors.remove(initialAnchor);
      for (String anchor : anchors) {
        var contextForAnchor = buildBrowseContext(context, anchor);
        var precedingQueryForAnchor = callNumberBrowseQueryProvider.get(request, contextForAnchor, false);
        var succeedingQueryForAnchor = callNumberBrowseQueryProvider.get(request, contextForAnchor, true);
        var responsesForAnchor = getBrowseAround(request, precedingQueryForAnchor, succeedingQueryForAnchor);

        if (isAnchorPresent(responsesForAnchor[1].getResponse(), contextForAnchor)) {
          context = contextForAnchor;
          precedingQuery = precedingQueryForAnchor;
          succeedingQuery = succeedingQueryForAnchor;
          responses = responsesForAnchor;
          break;
        }
      }
    }

    var precedingResult = callNumberBrowseResultConverter.convert(responses[0].getResponse(), context, false);
    var succeedingResult = callNumberBrowseResultConverter.convert(responses[1].getResponse(), context, true);
    var backwardSucceedingResult = callNumberBrowseResultConverter.convert(responses[1].getResponse(), context, false);

    var callNumberType = request.getRefinedCondition();
    var folioCallNumberTypes = folioCallNumberTypes();
    precedingResult.setRecords(excludeIrrelevantResultItems(context, callNumberType, folioCallNumberTypes,
      precedingResult.getRecords()));
    succeedingResult.setRecords(excludeIrrelevantResultItems(context, callNumberType, folioCallNumberTypes,
      succeedingResult.getRecords()));
    if (!backwardSucceedingResult.isEmpty()) {
      log.debug("browseAround:: backward succeeding result is not empty: Update preceding result");
      backwardSucceedingResult.setRecords(excludeIrrelevantResultItems(context, callNumberType, folioCallNumberTypes,
        backwardSucceedingResult.getRecords()));
      precedingResult.setRecords(mergeSafelyToList(backwardSucceedingResult.getRecords(), precedingResult.getRecords())
        .stream().distinct().toList());
    }
    var forwardPrecedingResult = callNumberBrowseResultConverter.convert(responses[0].getResponse(), context, true);
    if (!forwardPrecedingResult.isEmpty()) {
      log.debug("browseAround:: forward preceding result is not empty: Update preceding result");
      forwardPrecedingResult.setRecords(excludeIrrelevantResultItems(context, callNumberType, folioCallNumberTypes,
        forwardPrecedingResult.getRecords()));
      succeedingResult.setRecords(mergeSafelyToList(succeedingResult.getRecords(), forwardPrecedingResult.getRecords())
        .stream().distinct().toList());
    }

    if (precedingResult.getRecords().size() < request.getPrecedingRecordsCount()
        && precedingResult.getTotalRecords() > 0) {
      log.debug("getPrecedingResult:: preceding result is empty: Do additional requests");
      var additionalPrecedingRequestsResult = additionalRequests(request, context, precedingQuery,
        folioCallNumberTypes, false);
      precedingResult.setRecords(mergeSafelyToList(additionalPrecedingRequestsResult, precedingResult.getRecords())
        .stream().distinct().toList());
    }
    if (succeedingResult.getRecords().size() < request.getLimit() - request.getPrecedingRecordsCount()
        && succeedingResult.getTotalRecords() > 0) {
      log.debug("getSucceedingResult:: succeeding result is empty: Do additional requests");
      var additionalSucceedingRequestsResult = additionalRequests(request, context, succeedingQuery,
        folioCallNumberTypes, true);
      succeedingResult.setRecords(mergeSafelyToList(additionalSucceedingRequestsResult, succeedingResult.getRecords())
        .stream().distinct().toList());
    }

    // needed because result list might be modified in a scope of additional actions
    precedingResult.setRecords(precedingResult.getRecords().stream()
      .sorted(Comparator.comparing(CallNumberBrowseItem::getShelfKey))
      .toList());
    succeedingResult.setRecords(succeedingResult.getRecords().stream()
      .sorted(Comparator.comparing(CallNumberBrowseItem::getShelfKey))
      .toList());

    if (TRUE.equals(request.getHighlightMatch())) {
      highlightMatchingCallNumber(context, callNumber, succeedingResult);
    }

    return new BrowseResult<CallNumberBrowseItem>()
      .totalRecords(precedingResult.getTotalRecords() + succeedingResult.getTotalRecords())
      .prev(getPrevBrowsingValue(precedingResult.getRecords(), context, false))
      .next(getNextBrowsingValue(succeedingResult.getRecords(), context, true))
      .records(mergeSafelyToList(
        trim(precedingResult.getRecords(), context, false),
        trim(succeedingResult.getRecords(), context, true)));

  }

  private String callNumberFromRequest(BrowseRequest request) {
    return cqlSearchQueryConverter.convertToTermNode(request.getQuery(), request.getResource()).getTerm();
  }

  private List<String> getAnchors(BrowseRequest request) {
    var termNode = callNumberFromRequest(request);
    return effectiveShelvingOrderTermProcessor.getSearchTerms(termNode);
  }

  private List<String> getAnchors(String callNumber) {
    return effectiveShelvingOrderTermProcessor.getSearchTerms(callNumber);
  }

  @Override
  protected String getValueForBrowsing(CallNumberBrowseItem browseItem) {
    return browseItem.getShelfKey();
  }

  private MultiSearchResponse.Item[] getBrowseAround(BrowseRequest request,
                                                     SearchSourceBuilder precedingQuery,
                                                     SearchSourceBuilder succeedingQuery) {
    var multiSearchResponse = searchRepository.msearch(request, List.of(precedingQuery, succeedingQuery));

    return multiSearchResponse.getResponses();
  }

  private List<CallNumberBrowseItem> additionalRequests(BrowseRequest request,
                                                        BrowseContext context,
                                                        SearchSourceBuilder query,
                                                        Set<String> folioCallNumberTypes,
                                                        boolean isBrowsingForward) {
    List<CallNumberBrowseItem> additionalRecords = emptyList();
    int offset = query.from() + query.size();
    query.size(ADDITIONAL_REQUEST_SIZE);

    var precedingRecordsCount = request.getPrecedingRecordsCount();
    var desiredCount = isBrowsingForward ? request.getLimit() - precedingRecordsCount : precedingRecordsCount;
    while (additionalRecords.size() < desiredCount
           && query.from() <= searchConfig.getMaxBrowseRequestOffset()) {
      int size = query.size() < MAX_ADDITIONAL_REQUEST_SIZE ? query.size() * 2 : query.size();
      log.debug("additionalRequests:: browsingForward {} request offset {}, size {}",
        isBrowsingForward, offset, size);
      query.from(offset).size(size);

      var searchResponse = searchRepository.search(request, query);
      var totalHits = searchResponse.getHits().getTotalHits();
      if (totalHits == null || totalHits.value == 0) {
        log.debug("additionalRequests:: browsingForward {} response have no records", isBrowsingForward);
        break;
      }
      var result = callNumberBrowseResultConverter.convert(searchResponse, context, isBrowsingForward);
      var mergedList = mergeSafelyToList(additionalRecords, result.getRecords());
      additionalRecords = CallNumberUtils.excludeIrrelevantResultItems(context, request.getRefinedCondition(),
        folioCallNumberTypes, mergedList);
      offset = query.from() + query.size();
      log.debug("additionalRequests:: browsingForward {} response have new {} records",
        isBrowsingForward, result.getRecords().size());
    }
    return additionalRecords;
  }

  private boolean isAnchorPresent(SearchResponse searchResponse, BrowseContext context) {
    var items = callNumberBrowseResultConverter.convert(searchResponse, context, context.isBrowsingForward())
      .getRecords();

    return isNotEmpty(items) && StringUtils.equals(items.get(0).getShelfKey(), context.getAnchor());
  }

  private BrowseContext buildBrowseContext(BrowseContext context, String anchor) {
    return BrowseContext.builder()
      .precedingQuery(context.getPrecedingQuery())
      .succeedingQuery(context.getSucceedingQuery())
      .filters(context.getFilters())
      .anchor(anchor)
      .precedingLimit(context.getPrecedingLimit())
      .succeedingLimit(context.getSucceedingLimit())
      .build();
  }

  private Set<String> folioCallNumberTypes() {
    return referenceDataService.fetchReferenceData(CALL_NUMBER_TYPES, SOURCE, FOLIO_CALL_NUMBER_TYPES_SOURCES);
  }

  private static void highlightMatchingCallNumber(BrowseContext ctx,
                                                  String callNumber,
                                                  BrowseResult<CallNumberBrowseItem> result) {
    var items = result.getRecords();
    var anchor = ctx.getAnchor();

    if (isEmpty(items)) {
      result.setRecords(singletonList(getEmptyCallNumberBrowseItem(callNumber, anchor)));
      return;
    }

    var firstBrowseItem = items.get(0);
    if (!isAnchorMatching(firstBrowseItem, anchor)) {
      var browseItemsWithEmptyValue = new ArrayList<CallNumberBrowseItem>();
      browseItemsWithEmptyValue.add(getEmptyCallNumberBrowseItem(callNumber, anchor));
      browseItemsWithEmptyValue.addAll(items);
      result.setRecords(browseItemsWithEmptyValue);
      return;
    }

    firstBrowseItem.setIsAnchor(true);
  }

  private static boolean isAnchorMatching(CallNumberBrowseItem browseItem, String anchor) {
    var suffix = Optional.ofNullable(browseItem.getInstance())
      .flatMap(instance -> Optional.ofNullable(instance.getItems())
        .map(items -> items.isEmpty() ? null : items.get(0).getEffectiveCallNumberComponents().getSuffix()))
      .orElse(null);
    var shelfKey = browseItem.getShelfKey();
    var shelfKeyNoSuffix = StringUtils.removeEnd(shelfKey, suffix).trim();

    return StringUtils.equals(shelfKey, anchor)
      || StringUtils.equals(shelfKeyNoSuffix, anchor);
  }

  private static CallNumberBrowseItem getEmptyCallNumberBrowseItem(String callNumber, String shelfKey) {
    return new CallNumberBrowseItem()
      .fullCallNumber(callNumber)
      .shelfKey(shelfKey)
      .totalRecords(0)
      .isAnchor(true);
  }
}
