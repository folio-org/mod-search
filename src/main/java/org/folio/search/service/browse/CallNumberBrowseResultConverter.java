package org.folio.search.service.browse;

import static java.util.Collections.emptyList;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.toRootUpperCase;
import static org.folio.search.utils.CollectionUtils.findFirst;
import static org.folio.search.utils.CollectionUtils.reverse;
import static org.folio.search.utils.CollectionUtils.toStreamSafe;
import static org.folio.search.utils.SearchUtils.getEffectiveCallNumber;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.folio.search.domain.dto.CallNumberBrowseItem;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.model.SearchResult;
import org.folio.search.model.service.BrowseContext;
import org.folio.search.service.converter.ElasticsearchDocumentConverter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CallNumberBrowseResultConverter {

  private final ElasticsearchDocumentConverter documentConverter;

  /**
   * Converts received {@link SearchResponse} from Elasticsearch to browsing {@link SearchResult} object.
   *
   * @param resp - Elasticsearch {@link SearchResponse} object
   * @param ctx - {@link BrowseContext} value
   * @param isForwardBrowsing - direction of browsing
   * @return converted {@link SearchResult} object with {@link CallNumberBrowseItem} values
   */
  public SearchResult<CallNumberBrowseItem> convert(SearchResponse resp, BrowseContext ctx, boolean isForwardBrowsing) {
    var searchResult = documentConverter.convertToSearchResult(resp, Instance.class, this::mapToBrowseItem);
    var browseItems = searchResult.getRecords();
    if (CollectionUtils.isEmpty(browseItems)) {
      return searchResult;
    }

    var items = isForwardBrowsing ? browseItems : reverse(browseItems);
    var populatedItems = populateCallNumberBrowseItems(items, ctx, isForwardBrowsing);
    return searchResult.records(collapseCallNumberBrowseItems(populatedItems));
  }

  private CallNumberBrowseItem mapToBrowseItem(SearchHit searchHit, Instance instance) {
    var shelfKey = (String) searchHit.getSortValues()[0];
    return new CallNumberBrowseItem().totalRecords(1).instance(instance).shelfKey(shelfKey);
  }

  private static List<CallNumberBrowseItem> populateCallNumberBrowseItems(List<CallNumberBrowseItem> browseItems,
    BrowseContext ctx, boolean isBrowsingForward) {
    var lower = browseItems.get(0).getShelfKey();
    var upper = browseItems.get(browseItems.size() - 1).getShelfKey();
    return browseItems.stream()
      .map(item -> getCallNumberBrowseItemsBetween(item, lower, upper))
      .flatMap(Collection::stream)
      .filter(browseItem -> isValidBrowseItem(browseItem, ctx, isBrowsingForward))
      .sorted(comparing(CallNumberBrowseItem::getShelfKey))
      .collect(toList());
  }

  private static boolean isValidBrowseItem(CallNumberBrowseItem item, BrowseContext ctx, boolean isBrowsingForward) {
    var comparisonResult = item.getShelfKey().compareTo(ctx.getAnchor());
    if (comparisonResult == 0 && ctx.isAnchorIncluded(isBrowsingForward)) {
      return true;
    }

    return isBrowsingForward ? comparisonResult > 0 : comparisonResult < 0;
  }

  private static List<CallNumberBrowseItem> getCallNumberBrowseItemsBetween(CallNumberBrowseItem browseItem,
    String lower, String upper) {
    var itemsByShelfKeys = toStreamSafe(browseItem.getInstance().getItems())
      .filter(item -> StringUtils.isNotBlank(item.getEffectiveShelvingOrder()))
      .collect(groupingBy(item -> toRootUpperCase(item.getEffectiveShelvingOrder()), LinkedHashMap::new, toList()));

    return toStreamSafe(browseItem.getInstance().getItems())
      .map(Item::getEffectiveShelvingOrder)
      .filter(StringUtils::isNotBlank)
      .distinct()
      .map(StringUtils::toRootUpperCase)
      .filter(shelfKey -> shelfKey.compareTo(lower) >= 0 && shelfKey.compareTo(upper) <= 0)
      .map(shelfKey -> mapToCallNumberBrowseItem(browseItem, shelfKey, itemsByShelfKeys.get(shelfKey)))
      .collect(toList());
  }

  private static CallNumberBrowseItem mapToCallNumberBrowseItem(CallNumberBrowseItem browseItem,
    String shelfKey, List<Item> relatedItems) {
    var fullCallNumber = findFirst(relatedItems)
      .map(Item::getEffectiveCallNumberComponents)
      .map(cn -> getEffectiveCallNumber(cn.getPrefix(), cn.getCallNumber(), cn.getSuffix()))
      .orElse(null);

    return new CallNumberBrowseItem()
      .shelfKey(shelfKey)
      .fullCallNumber(fullCallNumber)
      .instance(browseItem.getInstance())
      .totalRecords(1);
  }

  private static List<CallNumberBrowseItem> collapseCallNumberBrowseItems(List<CallNumberBrowseItem> items) {
    if (items.isEmpty()) {
      return emptyList();
    }

    var collapsedItems = new ArrayList<CallNumberBrowseItem>();
    var iterator = items.iterator();
    var prevItem = iterator.next();
    collapsedItems.add(prevItem);

    CallNumberBrowseItem currItem;
    while (iterator.hasNext()) {
      currItem = iterator.next();
      if (Objects.equals(prevItem.getShelfKey(), currItem.getShelfKey())) {
        prevItem.instance(null).totalRecords(prevItem.getTotalRecords() + 1);
        continue;
      }
      collapsedItems.add(currItem);
      prevItem = currItem;
    }

    return collapsedItems;
  }
}
