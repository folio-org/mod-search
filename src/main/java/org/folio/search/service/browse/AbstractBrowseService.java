package org.folio.search.service.browse;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.folio.search.utils.LogUtils.collectionToLogMsg;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.log4j.Log4j2;
import org.folio.search.model.BrowseResult;
import org.folio.search.model.SearchResult;
import org.folio.search.model.service.BrowseContext;
import org.folio.search.model.service.BrowseRequest;
import org.folio.search.service.setter.SearchResponsePostProcessor;
import org.springframework.beans.factory.annotation.Autowired;

@Log4j2
public abstract class AbstractBrowseService<T> {

  private BrowseContextProvider browseContextProvider;
  private Map<Class<?>, SearchResponsePostProcessor<?>> searchResponsePostProcessors;

  /**
   * Finds related instances for call number browsing using given {@link BrowseRequest} object.
   *
   * @param request - service request as {@link BrowseRequest} object
   * @return {@link BrowseResult} with related instances by virtual shelf.
   */
  public BrowseResult<T> browse(BrowseRequest request) {
    log.debug("browse:: by [request: {}]", request);

    var context = browseContextProvider.get(request);
    if (isEmpty(context.getAnchor())) {
      return BrowseResult.empty();
    }
    return context.isBrowsingAround() ? browseAround(request, context) : browseInOneDirection(request, context);
  }

  /**
   * Defines the approach for browsing in one direction.
   *
   * @param request - {@link BrowseRequest} object for browsing
   * @param context - {@link BrowseContext} object with query, limits, etc.
   * @return {@link BrowseResult} with browsing items
   */
  protected abstract BrowseResult<T> browseInOneDirection(BrowseRequest request, BrowseContext context);

  /**
   * Defines the approach for browsing around.
   *
   * @param request - {@link BrowseRequest} object for browsing
   * @param context - {@link BrowseContext} object with query, limits, and etc.
   * @return {@link BrowseResult} with browsing items
   */
  protected abstract BrowseResult<T> browseAround(BrowseRequest request, BrowseContext context);

  /**
   * Injects {@link BrowseContextProvider} bean from spring context.
   *
   * @param browseContextProvider - {@link BrowseContextProvider} bean
   */
  @Autowired
  public void setBrowseContextProvider(BrowseContextProvider browseContextProvider) {
    this.browseContextProvider = browseContextProvider;
  }

  /**
   * Injects {@link SearchResponsePostProcessor} bean from spring context.
   *
   * @param searchResponsePostProcessors - {@link SearchResponsePostProcessor} bean
   */
  @Autowired
  public void setSearchResponsePostProcessors(Map<Class<?>, SearchResponsePostProcessor<?>>
                                                  searchResponsePostProcessors) {
    this.searchResponsePostProcessors = searchResponsePostProcessors;
  }

  /**
   * Returns the value for browsing as {@link String} from {@link T} item.
   *
   * @param browseItem - browse item to process.
   * @return value for next/prev field in browse response
   */
  protected abstract String getValueForBrowsing(T browseItem);

  protected static <T> List<T> trim(List<T> items, BrowseContext ctx, boolean isBrowsingForward) {
    return isBrowsingForward
      ? items.subList(0, min(ctx.getLimit(true), items.size()))
      : items.subList(max(items.size() - ctx.getLimit(false), 0), items.size());
  }

  protected String getPrevBrowsingValue(List<T> records, BrowseContext ctx, boolean isBrowsingForward) {
    log.debug("getPrevBrowsingValue:: by [records: {}, isBrowsingForward: {}]",
      collectionToLogMsg(records), isBrowsingForward);

    if (isBrowsingForward) {
      return getBrowsingValueByIndex(records, 0);
    }
    var limit = ctx.getLimit(false);
    return getBrowsingValueByIndex(records, limit, records.size() - limit);
  }

  protected String getNextBrowsingValue(List<T> records, BrowseContext ctx, boolean isBrowsingForward) {
    log.debug("getNextBrowsingValue:: by [records: {}, isBrowsingForward: {}]",
      collectionToLogMsg(records), isBrowsingForward);

    if (isBrowsingForward) {
      var limit = ctx.getLimit(true);
      return getBrowsingValueByIndex(records, limit, limit - 1);
    }
    return getBrowsingValueByIndex(records, records.size() - 1);
  }

  protected <R> void browseResultPostProcessing(Class<?> resourceClass, SearchResult<R> browseResult) {
    if (Objects.isNull(resourceClass)) {
      return;
    }
    var postProcessor = searchResponsePostProcessors.get(resourceClass);
    if (Objects.nonNull(postProcessor)) {
      postProcessor.process((List) browseResult.getRecords());
    }
  }

  private String getBrowsingValueByIndex(List<T> items, int index) {
    return isNotEmpty(items) ? getValueForBrowsing(items.get(index)) : null;
  }

  private String getBrowsingValueByIndex(List<T> items, int limit, int idx) {
    return items.size() <= limit ? null : getValueForBrowsing(items.get(idx));
  }
}
