package org.folio.search.service.browse;

import static java.lang.Boolean.TRUE;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
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

  private static final int ADDITIONAL_REQUEST_SIZE = 100;
  private static final int ADDITIONAL_REQUEST_SIZE_MAX = 500;
  private final SearchRepository searchRepository;
  private final CqlSearchQueryConverter cqlSearchQueryConverter;
  private final CallNumberBrowseQueryProvider callNumberBrowseQueryProvider;
  private final CallNumberBrowseResultConverter callNumberBrowseResultConverter;
  private final EffectiveShelvingOrderTermProcessor effectiveShelvingOrderTermProcessor;
  private final ReferenceDataService referenceDataService;
  static final List<String> FOLIO_CALL_NUMBER_TYPES_SOURCES = Collections.singletonList(FOLIO.getSource());

  @Override
  protected BrowseResult<CallNumberBrowseItem> browseInOneDirection(BrowseRequest request, BrowseContext context) {
    log.debug("browseInOneDirection:: by: [request: {}]", request);

    var isBrowsingForward = context.isBrowsingForward();
    var searchSource = callNumberBrowseQueryProvider.get(request, context, isBrowsingForward);
    var searchResponse = searchRepository.search(request, searchSource);

    if (!isAnchorPresent(searchResponse, context)) {
      var anchors = getAnchors(request);
      if (!anchors.isEmpty()) {
        anchors.remove(0);
      }
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
    browseResult.setRecords(excludeIrrelevantResultItems(request.getRefinedCondition(), folioCallNumberTypes, records));
    return new BrowseResult<CallNumberBrowseItem>()
      .records(trim(records, context, isBrowsingForward))
      .totalRecords(browseResult.getTotalRecords())
      .prev(getPrevBrowsingValue(records, context, isBrowsingForward))
      .next(getNextBrowsingValue(records, context, isBrowsingForward));
  }

  @Override
  protected BrowseResult<CallNumberBrowseItem> browseAround(BrowseRequest request, BrowseContext context) {
    log.debug("browseAround:: by: [request: {}]", request);

    var precedingQuery = callNumberBrowseQueryProvider.get(request, context, false);
    var responses = getBrowseAround(request, context, precedingQuery);

    var callNumber = callNumberFromRequest(request);

    if (!isAnchorPresent(responses[1].getResponse(), context)) {
      var anchors = getAnchors(callNumber);
      if (!anchors.isEmpty()) {
        anchors.remove(0);
      }
      for (String anchor : anchors) {
        var contextForAnchor = buildBrowseContext(context, anchor);
        var precedingQueryForAnchor = callNumberBrowseQueryProvider.get(request, contextForAnchor, false);
        var responsesForAnchor = getBrowseAround(request, contextForAnchor, precedingQueryForAnchor);

        if (isAnchorPresent(responsesForAnchor[1].getResponse(), contextForAnchor)) {
          context = contextForAnchor;
          precedingQuery = precedingQueryForAnchor;
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
    precedingResult.setRecords(excludeIrrelevantResultItems(callNumberType, folioCallNumberTypes,
      precedingResult.getRecords()));
    succeedingResult.setRecords(excludeIrrelevantResultItems(callNumberType, folioCallNumberTypes,
      succeedingResult.getRecords()));
    if (!backwardSucceedingResult.isEmpty()) {
      log.debug("browseAround:: backward succeeding result is not empty: Update preceding result");
      backwardSucceedingResult.setRecords(excludeIrrelevantResultItems(callNumberType, folioCallNumberTypes,
        backwardSucceedingResult.getRecords()));
      precedingResult.setRecords(mergeSafelyToList(backwardSucceedingResult.getRecords(), precedingResult.getRecords())
        .stream().distinct().toList());
    }

    if (precedingResult.getRecords().size() < request.getPrecedingRecordsCount()
        && precedingResult.getTotalRecords() > 0) {
      log.debug("getPrecedingResult:: preceding result are empty: Do additional requests");
      var additionalPrecedingRequestsResult = additionalPrecedingRequests(request, context, precedingQuery,
        folioCallNumberTypes);
      precedingResult.setRecords(mergeSafelyToList(additionalPrecedingRequestsResult, precedingResult.getRecords())
        .stream().distinct().toList());
    }

    if (TRUE.equals(request.getHighlightMatch())) {
      highlightMatchingCallNumber(context, callNumber, succeedingResult);
    }

    // needed because result list might be modified in a scope of additional actions
    precedingResult.setRecords(precedingResult.getRecords().stream()
      .sorted(Comparator.comparing(CallNumberBrowseItem::getShelfKey))
      .toList());

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

  private MultiSearchResponse.Item[] getBrowseAround(BrowseRequest request, BrowseContext context,
                                                     SearchSourceBuilder precedingQuery) {
    var succeedingQuery = callNumberBrowseQueryProvider.get(request, context, true);
    var multiSearchResponse = searchRepository.msearch(request, List.of(precedingQuery, succeedingQuery));

    return multiSearchResponse.getResponses();
  }

  private List<CallNumberBrowseItem> additionalPrecedingRequests(BrowseRequest request,
                                                                 BrowseContext context,
                                                                 SearchSourceBuilder precedingQuery,
                                                                 Set<String> folioCallNumberTypes) {
    List<CallNumberBrowseItem> additionalPrecedingRecords = emptyList();
    int offset = precedingQuery.from() + precedingQuery.size();
    precedingQuery.size(ADDITIONAL_REQUEST_SIZE);

    while (additionalPrecedingRecords.size() < request.getPrecedingRecordsCount()
           && precedingQuery.from() <= ADDITIONAL_REQUEST_SIZE_MAX) {
      int size = precedingQuery.size() * 2;
      log.debug("additionalPrecedingRequests:: request offset {}, size {}", offset, size);
      precedingQuery.from(offset).size(size);

      var searchResponse = searchRepository.search(request, precedingQuery);
      var totalHits = searchResponse.getHits().getTotalHits();
      if (totalHits == null || totalHits.value == 0) {
        log.debug("additionalPrecedingRequests:: response have no records");
        break;
      }
      var precedingResult = callNumberBrowseResultConverter.convert(searchResponse, context, false);
      var mergedList = mergeSafelyToList(additionalPrecedingRecords, precedingResult.getRecords());
      additionalPrecedingRecords = CallNumberUtils.excludeIrrelevantResultItems(request.getRefinedCondition(),
        folioCallNumberTypes, mergedList);
      offset = precedingQuery.from() + precedingQuery.size();
      log.debug("additionalPrecedingRequests:: response have new {} records", precedingResult.getRecords().size());
    }
    return additionalPrecedingRecords;
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
