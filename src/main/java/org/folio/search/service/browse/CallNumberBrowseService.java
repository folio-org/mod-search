package org.folio.search.service.browse;

import static java.lang.Boolean.TRUE;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Collections.singletonList;
import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.folio.search.utils.CollectionUtils.mergeSafelyToList;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.CallNumberBrowseItem;
import org.folio.search.model.SearchResult;
import org.folio.search.model.service.BrowseContext;
import org.folio.search.model.service.BrowseRequest;
import org.folio.search.repository.SearchRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CallNumberBrowseService extends AbstractBrowseService<CallNumberBrowseItem> {

  private final SearchRepository searchRepository;
  private final CallNumberBrowseQueryProvider callNumberBrowseQueryProvider;
  private final CallNumberBrowseResultConverter callNumberBrowseResultConverter;

  @Override
  protected SearchResult<CallNumberBrowseItem> browseInOneDirection(BrowseRequest request, BrowseContext context) {
    var isForwardBrowsing = context.isForwardBrowsing();
    var searchSource = callNumberBrowseQueryProvider.get(request, context, isForwardBrowsing);
    var searchResponse = searchRepository.search(request, searchSource);

    var searchResult = callNumberBrowseResultConverter.convert(searchResponse, context, isForwardBrowsing);
    searchResult.setRecords(trim(searchResult.getRecords(), context, isForwardBrowsing));
    return searchResult;
  }

  @Override
  protected SearchResult<CallNumberBrowseItem> browseAround(BrowseRequest request, BrowseContext context) {
    var precedingQuery = callNumberBrowseQueryProvider.get(request, context, false);
    var succeedingQuery = callNumberBrowseQueryProvider.get(request, context, true);
    var multiSearchResponse = searchRepository.msearch(request, List.of(precedingQuery, succeedingQuery));

    var responses = multiSearchResponse.getResponses();
    var precedingResult = callNumberBrowseResultConverter.convert(responses[0].getResponse(), context, false);
    var succeedingResult = callNumberBrowseResultConverter.convert(responses[1].getResponse(), context, true);

    if (TRUE.equals(request.getHighlightMatch())) {
      highlightMatchingCallNumber(context, succeedingResult);
    }

    var precedingRecords = trim(precedingResult.getRecords(), context, false);
    var succeedingRecords = trim(succeedingResult.getRecords(), context, true);
    return SearchResult.of(
      precedingResult.getTotalRecords() + succeedingResult.getTotalRecords(),
      mergeSafelyToList(precedingRecords, succeedingRecords));
  }

  private static void highlightMatchingCallNumber(BrowseContext ctx, SearchResult<CallNumberBrowseItem> result) {
    var items = result.getRecords();
    var anchor = ctx.getAnchor();

    if (isEmpty(items)) {
      result.setRecords(singletonList(getEmptyCallNumberBrowseItem(anchor)));
      return;
    }

    var firstBrowseItem = items.get(0);
    if (!StringUtils.equals(firstBrowseItem.getShelfKey(), anchor)) {
      var browseItemsWithEmptyValue = new ArrayList<CallNumberBrowseItem>();
      browseItemsWithEmptyValue.add(getEmptyCallNumberBrowseItem(anchor));
      browseItemsWithEmptyValue.addAll(items);
      result.setRecords(browseItemsWithEmptyValue);
      return;
    }

    firstBrowseItem.setIsAnchor(true);
  }

  private static CallNumberBrowseItem getEmptyCallNumberBrowseItem(String anchorCallNumber) {
    return new CallNumberBrowseItem().shelfKey(anchorCallNumber).totalRecords(0).isAnchor(true);
  }

  private static List<CallNumberBrowseItem> trim(List<CallNumberBrowseItem> items, BrowseContext ctx,
    boolean isForwardBrowsing) {
    return isForwardBrowsing
      ? items.subList(0, min(ctx.getLimit(true), items.size()))
      : items.subList(max(items.size() - ctx.getLimit(false), 0), items.size());
  }
}
