package org.folio.search.service.consortium;

import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.SPACE;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.wrap;
import static org.folio.search.utils.JdbcUtils.getFullTableName;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.model.Pair;
import org.folio.search.model.service.ConsortiumSearchContext;
import org.folio.search.model.types.ResourceType;
import org.folio.spring.FolioExecutionContext;

public class ConsortiumSearchQueryBuilder {

  static final String CONSORTIUM_INSTANCE_TABLE_NAME = "consortium_instance";
  public static final Map<ResourceType, String> CONSORTIUM_TABLES = Map.of(
    ResourceType.INSTANCE, CONSORTIUM_INSTANCE_TABLE_NAME,
    ResourceType.HOLDINGS, CONSORTIUM_INSTANCE_TABLE_NAME,
    ResourceType.ITEM, CONSORTIUM_INSTANCE_TABLE_NAME
  );
  private static final Map<ResourceType, List<String>> RESOURCE_FIELDS = Map.of(
    ResourceType.HOLDINGS,
    List.of("id", "hrid", "callNumberPrefix", "callNumber", "copyNumber", "permanentLocationId", "discoverySuppress"),
    ResourceType.ITEM,
    List.of("id", "hrid", "holdingsRecordId", "barcode")
  );

  private static final Map<ResourceType, Map<String, String>> RESOURCE_FILTER_DATABASE_NAME = Map.of(
    ResourceType.HOLDINGS, Map.of("instanceId", "instance_id", "tenantId", "tenant_id"),
    ResourceType.ITEM, Map.of("instanceId", "instance_id", "tenantId", "tenant_id")
  );

  private static final Map<ResourceType, List<String>> RESOURCE_JSONB_FILTERS = Map.of(
    ResourceType.ITEM, List.of("holdingsRecordId")
  );

  private static final Map<ResourceType, String> RESOURCE_COLLECTION_NAME = Map.of(
    ResourceType.HOLDINGS, "holdings",
    ResourceType.ITEM, "items"
  );
  private final ConsortiumSearchContext searchContext;
  private final ResourceType resourceType;
  private final List<Pair<String, String>> filters;
  private final List<Pair<String, String>> jsonbFilters;

  public ConsortiumSearchQueryBuilder(ConsortiumSearchContext searchContext) {
    this.searchContext = searchContext;
    this.resourceType = searchContext.getResourceType();
    this.filters = prepareFilters(resourceType, emptyList(), RESOURCE_JSONB_FILTERS.get(resourceType));
    this.jsonbFilters = prepareFilters(resourceType, RESOURCE_JSONB_FILTERS.get(resourceType),
      RESOURCE_FILTER_DATABASE_NAME.get(resourceType).values());
  }

  public String buildSelectQuery(FolioExecutionContext context) {
    var fullTableName = getFullTableName(context, CONSORTIUM_TABLES.get(resourceType));
    var resourceCollection = RESOURCE_COLLECTION_NAME.get(resourceType);
    String subQuery = "SELECT instance_id, tenant_id, json_array_elements(json -> '" + resourceCollection + "') "
                      + "as " + resourceCollection + " FROM " + fullTableName + SPACE + getWhereClause(filters, null);
    String query = "SELECT i.instance_id as instanceId, i.tenant_id as tenantId,"
                   + getSelectors("i." + resourceCollection, RESOURCE_FIELDS.get(resourceType))
                   + " FROM (" + subQuery + ") i"
                   + getWhereClause(jsonbFilters, "i." + resourceCollection)
                   + getOrderByClause()
                   + getLimitClause()
                   + getOffsetClause();
    return StringUtils.normalizeSpace(query);
  }

  public Object[] getQueryArguments() {
    return Stream.concat(filters.stream(), jsonbFilters.stream())
      .map(Pair::getSecond)
      .toArray();
  }

  private String getOffsetClause() {
    if (searchContext.getOffset() == null) {
      return EMPTY;
    }
    return wrapped("OFFSET " + searchContext.getOffset());
  }

  private String wrapped(String str) {
    return wrap(str, ' ');
  }

  private String getLimitClause() {
    if (searchContext.getLimit() == null) {
      return EMPTY;
    }
    return wrapped("LIMIT " + searchContext.getLimit());
  }

  private String getOrderByClause() {
    var sortBy = searchContext.getSortBy();
    if (isBlank(sortBy)) {
      return EMPTY;
    }
    var sortOrder = searchContext.getSortOrder();
    return wrapped("ORDER BY " + sortBy + SPACE + (sortOrder == null ? EMPTY : sortOrder.getValue()));
  }

  private String getJsonSelector(String source, String field) {
    return source + " ->> " + wrap(field, '\'');
  }

  private String getSelectors(String source, List<String> sourceFields) {
    return wrap(sourceFields.stream()
      .map(field -> getJsonSelector(source, field) + " AS " + field)
      .collect(Collectors.joining(", ")), ' ');
  }

  private String getWhereClause(List<Pair<String, String>> filters, String source) {
    if (filters.isEmpty()) {
      return EMPTY;
    }
    var conditionsClause = filters.stream()
      .map(filter -> (StringUtils.isNotBlank(source)
                      ? getJsonSelector(source, filter.getFirst())
                      : filter.getFirst()) + " = ?")
      .collect(Collectors.joining(" AND "));
    return conditionsClause.isBlank() ? conditionsClause : wrapped("WHERE " + conditionsClause);
  }

  private List<Pair<String, String>> prepareFilters(ResourceType resourceType,
                                                    List<String> includeFilters, Collection<String> excludeFilters) {
    var mappedFilterNames = RESOURCE_FILTER_DATABASE_NAME.get(resourceType);
    return searchContext.getFilters().stream()
      .map(filter -> {
        if (mappedFilterNames.containsKey(filter.getFirst())) {
          return Pair.pair(mappedFilterNames.get(filter.getFirst()), filter.getSecond());
        }
        return filter;
      })
      .filter(filter -> {
        if (CollectionUtils.isNotEmpty(includeFilters)) {
          return includeFilters.contains(filter.getFirst());
        }
        if (CollectionUtils.isNotEmpty(excludeFilters)) {
          return !excludeFilters.contains(filter.getFirst());
        }
        return true;
      })
      .toList();
  }

}

