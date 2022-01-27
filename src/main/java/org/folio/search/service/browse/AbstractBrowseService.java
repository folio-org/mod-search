package org.folio.search.service.browse;

import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.folio.search.cql.CqlSearchQueryConverter;
import org.folio.search.model.SearchResult;
import org.folio.search.model.service.BrowseContext;
import org.folio.search.model.service.BrowseRequest;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractBrowseService<T> {

  protected CqlSearchQueryConverter cqlSearchQueryConverter;

  /**
   * Finds related instances for call number browsing using given {@link BrowseRequest} object.
   *
   * @param request - service request as {@link BrowseRequest} object
   * @return search result with related instances by virtual shelf.
   */
  public SearchResult<T> browse(BrowseRequest request) {
    var cqlSearchSource = cqlSearchQueryConverter.convert(request.getQuery(), request.getResource());
    var context = getBrowseContext(request, cqlSearchSource);
    return context.isAroundBrowsing() ? browseAround(request, context) : browseInOneDirection(request, context);
  }

  protected BrowseContext getBrowseContext(BrowseRequest request, SearchSourceBuilder cqlSearchSource) {
    return BrowseContext.of(request, cqlSearchSource);
  }

  protected abstract SearchResult<T> browseInOneDirection(BrowseRequest request, BrowseContext context);

  protected abstract SearchResult<T> browseAround(BrowseRequest request, BrowseContext context);

  @Autowired
  public void setCqlSearchQueryConverter(CqlSearchQueryConverter cqlSearchQueryConverter) {
    this.cqlSearchQueryConverter = cqlSearchQueryConverter;
  }
}
