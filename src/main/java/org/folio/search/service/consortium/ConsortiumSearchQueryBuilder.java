package org.folio.search.service.consortium;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.SPACE;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.wrap;
import static org.folio.search.utils.JdbcUtils.getFullTableName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.model.Pair;
import org.folio.search.model.service.ConsortiumSearchContext;
import org.folio.search.model.types.ResourceType;
import org.folio.spring.FolioExecutionContext;

public class ConsortiumSearchQueryBuilder {

  static final String CONSORTIUM_INSTANCE_TABLE_NAME = "consortium_instance";
  public static final Map<ResourceType, String> CONSORTIUM_TABLES = Map.of(
    ResourceType.INSTANCE, CONSORTIUM_INSTANCE_TABLE_NAME,
    ResourceType.HOLDINGS, CONSORTIUM_INSTANCE_TABLE_NAME
  );
  private static final Map<ResourceType, List<String>> RESOURCE_FIELDS = Map.of(
    ResourceType.HOLDINGS,
    List.of("id", "hrid", "callNumberPrefix", "callNumber", "copyNumber", "permanentLocationId", "discoverySuppress")
  );

  private static final Map<ResourceType, Map<String, String>> RESOURCE_FILTER_DATABASE_NAME = Map.of(
    ResourceType.HOLDINGS, Map.of("instanceId", "instance_id", "tenantId", "tenant_id")
  );
  private final ConsortiumSearchContext searchContext;
  private final List<Pair<String, String>> filters;

  public ConsortiumSearchQueryBuilder(ConsortiumSearchContext searchContext) {
    this.searchContext = searchContext;
    this.filters = getFilters(searchContext.getResourceType());
  }

  public String buildSelectQuery(FolioExecutionContext context) {
    var resourceType = searchContext.getResourceType();
    var fullTableName = getFullTableName(context, CONSORTIUM_TABLES.get(resourceType));
    String subQuery = "SELECT instance_id, tenant_id, json_array_elements(json -> 'holdings') as holdings FROM "
                      + fullTableName + SPACE + getWhereClause(filters);
    String query = "SELECT i.instance_id as instanceId, i.tenant_id as tenantId,"
                   + getSelectors("i.holdings", RESOURCE_FIELDS.get(resourceType))
                   + " FROM (" + subQuery + ") i"
                   + getOrderByClause()
                   + getLimitClause()
                   + getOffsetClause();
    return StringUtils.normalizeSpace(query);
  }

  public PreparedStatement buildSelectQuery(FolioExecutionContext context, Connection con) throws SQLException {
    var preparedStatement = con.prepareStatement(buildSelectQuery(context));
    for (int i = 0; i < filters.size(); i++) {
      preparedStatement.setString(i + 1, filters.get(i).getSecond());
    }
    return preparedStatement;
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

  private String getWhereClause(List<Pair<String, String>> filters) {
    if (filters.isEmpty()) {
      return EMPTY;
    }
    var conditionsClause = filters.stream()
      .map(filter -> filter.getFirst() + " = ?")
      .collect(Collectors.joining(" AND "));
    return conditionsClause.isBlank() ? conditionsClause : "WHERE " + conditionsClause;
  }

  private List<Pair<String, String>> getFilters(ResourceType resourceType) {
    var mappedFilterNames = RESOURCE_FILTER_DATABASE_NAME.get(resourceType);
    return searchContext.getFilters().stream()
      .map(filter -> {
        if (mappedFilterNames.containsKey(filter.getFirst())) {
          return Pair.pair(mappedFilterNames.get(filter.getFirst()), filter.getSecond());
        }
        return filter;
      }).toList();
  }

}

