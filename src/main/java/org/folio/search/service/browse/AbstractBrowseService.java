package org.folio.search.service.browse;

import static java.lang.Math.max;
import static java.lang.Math.min;

import java.util.List;
import org.folio.search.model.BrowseResult;
import org.folio.search.model.service.BrowseContext;
import org.folio.search.model.service.BrowseRequest;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractBrowseService<T> {

  private BrowseContextProvider browseContextProvider;

  /**
   * Finds related instances for call number browsing using given {@link BrowseRequest} object.
   *
   * @param request - service request as {@link BrowseRequest} object
   * @return {@link BrowseResult} with related instances by virtual shelf.
   */
  public BrowseResult<T> browse(BrowseRequest request) {
    var context = browseContextProvider.get(request);
    return context.isAroundBrowsing() ? browseAround(request, context) : browseInOneDirection(request, context);
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

  protected static <T> List<T> trim(List<T> items, BrowseContext ctx, boolean isBrowsingForward) {
    return isBrowsingForward
      ? items.subList(0, min(ctx.getLimit(true), items.size()))
      : items.subList(max(items.size() - ctx.getLimit(false), 0), items.size());
  }
}
