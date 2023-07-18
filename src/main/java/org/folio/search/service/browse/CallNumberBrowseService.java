package org.folio.search.service.browse;

import static java.lang.Boolean.TRUE;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.folio.search.utils.CollectionUtils.mergeSafelyToList;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.cql.CqlSearchQueryConverter;
import org.folio.search.domain.dto.CallNumberBrowseItem;
import org.folio.search.model.BrowseResult;
import org.folio.search.model.service.BrowseContext;
import org.folio.search.model.service.BrowseRequest;
import org.folio.search.repository.SearchRepository;
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

  @Override
  protected BrowseResult<CallNumberBrowseItem> browseInOneDirection(BrowseRequest request, BrowseContext context) {
    log.debug("browseInOneDirection:: by: [request: {}]", request);

    var isBrowsingForward = context.isBrowsingForward();
    var searchSource = callNumberBrowseQueryProvider.get(request, context, isBrowsingForward);
    var searchResponse = searchRepository.search(request, searchSource);
    var browseResult = callNumberBrowseResultConverter.convert(searchResponse, context, isBrowsingForward);
    var records = browseResult.getRecords();
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
    var succeedingQuery = callNumberBrowseQueryProvider.get(request, context, true);
    var multiSearchResponse = searchRepository.msearch(request, List.of(precedingQuery, succeedingQuery));

    var responses = multiSearchResponse.getResponses();
    var precedingResult = callNumberBrowseResultConverter.convert(responses[0].getResponse(), context, false);
    var succeedingResult = callNumberBrowseResultConverter.convert(responses[1].getResponse(), context, true);
    var backwardSucceedingResult = callNumberBrowseResultConverter.convert(responses[1].getResponse(), context, false);

    if (!backwardSucceedingResult.isEmpty()) {
      log.debug("browseAround:: backward succeeding result is not empty: Update preceding result");
      precedingResult.setRecords(mergeSafelyToList(backwardSucceedingResult.getRecords(), precedingResult.getRecords())
        .stream().distinct().toList());
    }

    if (precedingResult.getRecords().size() < request.getPrecedingRecordsCount()
        && precedingResult.getTotalRecords() > 0) {
      log.debug("getPrecedingResult:: preceding result are empty: Do additional requests");
      var additionalPrecedingRequestsResult = additionalPrecedingRequests(request, context, precedingQuery);
      precedingResult.setRecords(mergeSafelyToList(additionalPrecedingRequestsResult, precedingResult.getRecords())
        .stream().distinct().toList());
    }

    if (TRUE.equals(request.getHighlightMatch())) {
      var callNumber = cqlSearchQueryConverter.convertToTermNode(request.getQuery(), request.getResource()).getTerm();
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

  @Override
  protected String getValueForBrowsing(CallNumberBrowseItem browseItem) {
    return browseItem.getShelfKey();
  }

  private List<CallNumberBrowseItem> additionalPrecedingRequests(BrowseRequest request,
                                                                         BrowseContext context,
                                                                         SearchSourceBuilder precedingQuery) {
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
      additionalPrecedingRecords = mergeSafelyToList(additionalPrecedingRecords, precedingResult.getRecords());
      offset = precedingQuery.from() + precedingQuery.size();
      log.debug("additionalPrecedingRequests:: response have new {} records", precedingResult.getRecords().size());
    }
    return additionalPrecedingRecords;
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
    if (!StringUtils.equals(firstBrowseItem.getShelfKey(), anchor)) {
      var browseItemsWithEmptyValue = new ArrayList<CallNumberBrowseItem>();
      browseItemsWithEmptyValue.add(getEmptyCallNumberBrowseItem(callNumber, anchor));
      browseItemsWithEmptyValue.addAll(items);
      result.setRecords(browseItemsWithEmptyValue);
      return;
    }

    firstBrowseItem.setIsAnchor(true);
  }

  private static CallNumberBrowseItem getEmptyCallNumberBrowseItem(String callNumber, String shelfKey) {
    return new CallNumberBrowseItem()
      .fullCallNumber(callNumber)
      .shelfKey(shelfKey)
      .totalRecords(0)
      .isAnchor(true);
  }
}
