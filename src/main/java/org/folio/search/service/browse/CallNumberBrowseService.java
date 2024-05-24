package org.folio.search.service.browse;

import static java.lang.Boolean.TRUE;
import static java.util.Collections.singletonList;
import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.folio.search.model.types.CallNumberTypeSource.FOLIO;
import static org.folio.search.utils.CollectionUtils.mergeSafelyToList;

import java.util.ArrayList;
import java.util.Collections;
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
import org.opensearch.search.sort.SortOrder;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class CallNumberBrowseService extends AbstractBrowseService<CallNumberBrowseItem> {

  public static final List<String> FOLIO_CALL_NUMBER_TYPES_SOURCES = Collections.singletonList(FOLIO.getSource());
  private static final int ADDITIONAL_REQUEST_SIZE = 100;
  private final SearchRepository searchRepository;
  private final CqlSearchQueryConverter cqlSearchQueryConverter;
  private final CallNumberBrowseQueryProvider callNumberBrowseQueryProvider;
  private final CallNumberBrowseResultConverter callNumberBrowseResultConverter;

  @Override
  protected BrowseResult<CallNumberBrowseItem> browseInOneDirection(BrowseRequest request, BrowseContext context) {
    var isBrowsingForward = context.isBrowsingForward();
    var searchSource = callNumberBrowseQueryProvider.get(request, context, isBrowsingForward);
    var searchResponse = searchRepository.search(request, searchSource);
    var browseResult = callNumberBrowseResultConverter.convert(searchResponse, context, request, isBrowsingForward);
    var records = browseResult.getRecords();
    var browseItems = trim(records, context, isBrowsingForward);
    if (browseItems.isEmpty()) {
      return new BrowseResult<CallNumberBrowseItem>()
        .records(browseItems)
        .totalRecords(browseResult.getTotalRecords());
    }

    var callNumberBrowseItemFirst = browseItems.get(0);
    searchSource.sorts().clear();
    searchSource.searchAfter(new Object[]{callNumberBrowseItemFirst.getShelfKey()})
      .sort("itemEffectiveShelvingOrder", SortOrder.DESC)
      .from(0)
      .size(5);
    String prev = null;
    var precedingResponse = searchRepository.search(request, searchSource);
    if (precedingResponse.getHits() != null
        && precedingResponse.getHits().getTotalHits() != null
        && precedingResponse.getHits().getTotalHits().value > 0) {
      prev = callNumberBrowseItemFirst.getShelfKey();
    }
    searchSource.sorts().clear();
    var callNumberBrowseItemLast = browseItems.get(browseItems.size() - 1);
    searchSource.searchAfter(new Object[]{callNumberBrowseItemLast.getShelfKey()})
      .sort("itemEffectiveShelvingOrder", SortOrder.ASC)
      .from(0)
      .size(5);
    String next = null;
    var succedingResponse = searchRepository.search(request, searchSource);
    if (succedingResponse.getHits() != null
        && succedingResponse.getHits().getTotalHits() != null
        && succedingResponse.getHits().getTotalHits().value > 0) {
      next = callNumberBrowseItemLast.getShelfKey();
    }
    return new BrowseResult<CallNumberBrowseItem>()
      .records(browseItems)
      .totalRecords(browseResult.getTotalRecords())
      .prev(prev)
      .next(next);
  }

  @Override
  protected BrowseResult<CallNumberBrowseItem> browseAround(BrowseRequest request, BrowseContext context) {
    var precedingQuery = callNumberBrowseQueryProvider.get(request, context, false);
    var succeedingQuery = callNumberBrowseQueryProvider.get(request, context, true);
    var multiSearchResponse = searchRepository.msearch(request, List.of(precedingQuery, succeedingQuery));

    var responses = multiSearchResponse.getResponses();
    var precedingResult = callNumberBrowseResultConverter.convert(responses[0].getResponse(), context, request, false);
    var succeedingResult = callNumberBrowseResultConverter.convert(responses[1].getResponse(), context, request, true);

    if (precedingResult.getRecords().isEmpty() && precedingResult.getTotalRecords() > 0) {
      log.debug("browseAround:: preceding result are empty: Do additional requests");
      precedingResult = additionalPrecedingRequests(request, context, precedingQuery);
    }

    if (TRUE.equals(request.getHighlightMatch())) {
      var callNumber = cqlSearchQueryConverter.convertToTermNode(request.getQuery(), request.getResource()).getTerm();
      highlightMatchingCallNumber(context, callNumber, succeedingResult);
    }

    var browseItems = mergeSafelyToList(
      trim(precedingResult.getRecords(), context, false),
      trim(succeedingResult.getRecords(), context, true));

    if (browseItems.isEmpty()) {
      return new BrowseResult<CallNumberBrowseItem>()
        .records(browseItems)
        .totalRecords(browseItems.size());
    }

    String prev = null;
    var callNumberBrowseItemFirst = browseItems.get(0);
    precedingQuery.searchAfter(new Object[]{callNumberBrowseItemFirst.getShelfKey()})
      .from(0).size(5);
    var precedingResponse = searchRepository.search(request, precedingQuery);
    if (precedingResponse.getHits() != null
        && precedingResponse.getHits().getTotalHits() != null
        && precedingResponse.getHits().getTotalHits().value > 0) {
      prev = callNumberBrowseItemFirst.getShelfKey();
    }
    String next = null;
    var callNumberBrowseItemLast = browseItems.get(browseItems.size() - 1);
    succeedingQuery.searchAfter(new Object[]{callNumberBrowseItemLast.getShelfKey()})
      .from(0).size(5);
    var succeedingResponse = searchRepository.search(request, succeedingQuery);
    if (succeedingResponse.getHits() != null
        && succeedingResponse.getHits().getTotalHits() != null
        && succeedingResponse.getHits().getTotalHits().value > 0) {
      next = callNumberBrowseItemLast.getShelfKey();
    }

    return new BrowseResult<CallNumberBrowseItem>()
      .totalRecords(precedingResult.getTotalRecords() + succeedingResult.getTotalRecords())
      .prev(prev)
      .next(next)
      .records(browseItems);

  }

  @Override
  protected String getValueForBrowsing(CallNumberBrowseItem browseItem) {
    return browseItem.getShelfKey();
  }

  private BrowseResult<CallNumberBrowseItem> additionalPrecedingRequests(BrowseRequest request,
                                                                         BrowseContext context,
                                                                         SearchSourceBuilder precedingQuery) {
    BrowseResult<CallNumberBrowseItem> precedingResult = BrowseResult.empty();
    precedingQuery.size(ADDITIONAL_REQUEST_SIZE);

    while (precedingResult.getRecords().isEmpty()) {
      int offset = precedingQuery.from() + precedingQuery.size();
      int size = precedingQuery.size() * 2;
      if (offset + size >= 10000) {
        break;
      }
      log.debug("additionalPrecedingRequests:: request offset {}, size {}", offset, size);
      precedingQuery.from(offset).size(size);

      var searchResponse = searchRepository.search(request, precedingQuery);
      var totalHits = searchResponse.getHits().getTotalHits();
      if (totalHits == null || totalHits.value == 0) {
        log.debug("additionalPrecedingRequests:: response have no records");
        break;
      }
      precedingResult = callNumberBrowseResultConverter.convert(searchResponse, context, request, false);
    }
    return precedingResult;
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
