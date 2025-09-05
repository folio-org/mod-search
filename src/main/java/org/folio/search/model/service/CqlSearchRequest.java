package org.folio.search.model.service;

import java.util.ArrayList;
import java.util.List;
import org.folio.search.model.ResourceRequest;
import org.folio.search.model.types.ResourceType;
import org.folio.search.utils.SearchUtils;

/**
 * CQL based search request model.
 *
 * @param resource               Resource name.
 * @param resourceClass          Resource class for response.
 * @param tenantId               Request tenant id.
 * @param query                  A CQL query string with search conditions.
 * @param limit                  Limit the number of elements in the response.
 * @param offset                 Skip over a number of elements by specifying an offset value for the query.
 * @param expandAll              Whether to return only basic properties or entire record.
 * @param includeNumberOfTitles  Whether to return only basic properties or entire record.
 * @param includeFields          Retrieves the fields listed in the specified parameter.
 * @param consortiumConsolidated Doesn't affect non-consortium. true means include all records,
 *                               false means filter for active affiliation.
 */
public record CqlSearchRequest<T>(
  ResourceType resource,
  Class<T> resourceClass,
  String tenantId,
  String query,
  Integer limit,
  Integer offset,
  Boolean expandAll,
  Boolean includeNumberOfTitles,
  List<String> includeFields,
  Boolean consortiumConsolidated
) implements ResourceRequest {

  public static <R> Builder<R> builder(Class<R> resourceClass) {
    return new Builder<>(resourceClass);
  }

  public static final class Builder<T> {

    private final Class<T> resourceClass;
    private final ResourceType resource;
    private String tenantId;
    private String query;
    private Integer limit;
    private Integer offset;
    private Boolean expandAll;
    private Boolean includeNumberOfTitles = Boolean.TRUE;
    private List<String> includeFields = new ArrayList<>();
    private Boolean consortiumConsolidated = Boolean.FALSE;

    private Builder(Class<T> resourceClass) {
      this.resourceClass = resourceClass;
      this.resource = ResourceType.byName(SearchUtils.getResourceName(resourceClass));
    }

    public Builder<T> tenantId(String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder<T> query(String query) {
      this.query = query;
      return this;
    }

    public Builder<T> limit(Integer limit) {
      this.limit = limit;
      return this;
    }

    public Builder<T> offset(Integer offset) {
      this.offset = offset;
      return this;
    }

    public Builder<T> expandAll(Boolean expandAll) {
      this.expandAll = expandAll;
      return this;
    }

    public Builder<T> includeNumberOfTitles(Boolean includeNumberOfTitles) {
      this.includeNumberOfTitles = includeNumberOfTitles;
      return this;
    }

    public Builder<T> includeFields(String include) {
      this.includeFields = ResourceRequest.parseIncludeField(include);
      return this;
    }

    public Builder<T> consortiumConsolidated(Boolean consortiumConsolidated) {
      this.consortiumConsolidated = consortiumConsolidated;
      return this;
    }

    public CqlSearchRequest<T> build() {
      return new CqlSearchRequest<>(resource, resourceClass, tenantId, query, limit, offset, expandAll,
        includeNumberOfTitles, includeFields, consortiumConsolidated);
    }
  }
}
