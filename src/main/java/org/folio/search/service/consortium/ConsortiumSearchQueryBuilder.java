package org.folio.search.service.consortium;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.SPACE;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.wrap;
import static org.folio.search.utils.JdbcUtils.getFullTableName;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.model.Pair;
import org.folio.search.model.service.ConsortiumSearchContext;
import org.folio.search.model.types.ResourceType;
import org.folio.spring.FolioExecutionContext;

public class ConsortiumSearchQueryBuilder {

  static final String CONSORTIUM_INSTANCE_TABLE_NAME = "consortium_instance";
  static final String HOLDING_TABLE_NAME = "holding";
  static final String ITEM_TABLE_NAME = "item";

  public static final Map<ResourceType, String> CONSORTIUM_TABLES = Map.of(
    ResourceType.INSTANCE, CONSORTIUM_INSTANCE_TABLE_NAME,
    ResourceType.HOLDINGS, HOLDING_TABLE_NAME,
    ResourceType.ITEM, ITEM_TABLE_NAME
  );
  private static final Map<ResourceType, List<String>> RESOURCE_FIELDS = Map.of(
    ResourceType.HOLDINGS,
    List.of("hrid", "callNumberPrefix", "callNumber", "callNumberSuffix",
      "copyNumber", "permanentLocationId", "discoverySuppress"),
    ResourceType.ITEM,
    List.of("hrid", "holdingsRecordId", "barcode")
  );

  private static final Map<ResourceType, Map<String, String>> RESOURCE_FILTER_DATABASE_NAME = Map.of(
    ResourceType.HOLDINGS, Map.of("instanceId", "instance_id", "tenantId", "tenant_id"),
    ResourceType.ITEM, Map.of("instanceId", "instance_id", "tenantId", "tenant_id", "holdingsRecordId", "holding_id")
  );

  private static final Map<String, String> COLUMN_CASTS = Map.of(
    "instance_id", "uuid",
    "holding_id", "uuid"
  );

  private final ConsortiumSearchContext searchContext;
  private final ResourceType resourceType;
  private final List<Pair<String, String>> filters;

  public ConsortiumSearchQueryBuilder(ConsortiumSearchContext searchContext) {
    this.searchContext = searchContext;
    this.resourceType = searchContext.getResourceType();
    this.filters = prepareFilters(resourceType);
  }

  public String buildSelectQuery(FolioExecutionContext context) {
    var fullTableName = getFullTableName(context, CONSORTIUM_TABLES.get(resourceType));
    String query = "SELECT i.id as id, i.instance_id as instanceId, i.tenant_id as tenantId,"
                   + getSelectors("i.json", RESOURCE_FIELDS.get(resourceType))
                   + " FROM " + fullTableName + " i"
                   + getWhereClause(filters, null)
                   + getOrderByClause()
                   + getLimitClause()
                   + getOffsetClause();
    return StringUtils.normalizeSpace(query);
  }

  public String buildCountQuery(FolioExecutionContext context) {
    var fullTableName = getFullTableName(context, CONSORTIUM_TABLES.get(resourceType));
    String query = "SELECT count(*) FROM " + fullTableName + " i"
                   + getWhereClause(filters, null);
    return StringUtils.normalizeSpace(query);
  }

  public Object[] getQueryArguments() {
    return filters.stream()
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
                      : filter.getFirst()) + " = ?" + getCast(filter.getFirst()))
      .collect(Collectors.joining(" AND "));
    return conditionsClause.isBlank() ? conditionsClause : wrapped("WHERE " + conditionsClause);
  }

  private String getCast(String column) {
    return Optional.ofNullable(COLUMN_CASTS.get(column)).map(cast -> "::" + cast).orElse(EMPTY);
  }

  private List<Pair<String, String>> prepareFilters(ResourceType resourceType) {
    var mappedFilterNames = RESOURCE_FILTER_DATABASE_NAME.get(resourceType);
    return searchContext.getFilters().stream()
      .map(filter -> {
        if (mappedFilterNames.containsKey(filter.getFirst())) {
          return Pair.pair(mappedFilterNames.get(filter.getFirst()), filter.getSecond());
        }
        return filter;
      })
      .toList();
  }

}

