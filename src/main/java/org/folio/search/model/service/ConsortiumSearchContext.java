package org.folio.search.model.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.SortOrder;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.Pair;
import org.folio.search.model.types.ResourceType;

@Getter
public class ConsortiumSearchContext {

  static final String SORT_NOT_ALLOWED_MSG = "Not allowed sort field for %s";
  static final String FILTER_REQUIRED_MSG = "At least one filter criteria required";
  static final String INSTANCE_ID_FILTER_REQUIRED_MSG = "instanceId filter is required";

  private static final Map<ResourceType, List<String>> ALLOWED_SORT_FIELDS = Map.of(
    ResourceType.HOLDINGS, List.of("id", "hrid", "tenantId", "instanceId",
      "callNumberPrefix", "callNumber", "callNumberSuffix", "copyNumber", "permanentLocationId"),
    ResourceType.ITEM, List.of("id", "hrid", "tenantId", "instanceId", "holdingsRecordId", "barcode")
  );

  private final ResourceType resourceType;
  private final List<Pair<String, String>> filters;
  private final Integer limit;
  private final Integer offset;
  private final String sortBy;
  private final SortOrder sortOrder;

  ConsortiumSearchContext(ResourceType resourceType, List<Pair<String, String>> filters, Integer limit, Integer offset,
                          String sortBy, SortOrder sortOrder) {
    this.resourceType = resourceType;
    this.filters = filters;

    if (ResourceType.ITEM == resourceType) {
      boolean instanceIdFilterExist = filters.stream().anyMatch(filter -> filter.getFirst().equals("instanceId"));
      if (!instanceIdFilterExist) {
        throw new RequestValidationException(INSTANCE_ID_FILTER_REQUIRED_MSG, null, null);
      }
    }
    if (sortBy != null && !ALLOWED_SORT_FIELDS.get(resourceType).contains(sortBy)) {
      throw new RequestValidationException(SORT_NOT_ALLOWED_MSG.formatted(resourceType.getValue()), "sortBy", sortBy);
    }
    if (sortBy != null && (filters.isEmpty())) {
      throw new RequestValidationException(FILTER_REQUIRED_MSG, null, null);
    }
    this.limit = limit;
    this.offset = offset;
    this.sortBy = sortBy;
    this.sortOrder = sortOrder;
  }

  public static ConsortiumSearchContextBuilder builderFor(ResourceType resourceType) {
    return new ConsortiumSearchContextBuilder(resourceType);
  }

  public static class ConsortiumSearchContextBuilder {
    private final ResourceType resourceType;
    private final List<Pair<String, String>> filters = new ArrayList<>();
    private Integer limit;
    private Integer offset;
    private String sortBy;
    private SortOrder sortOrder;

    ConsortiumSearchContextBuilder(ResourceType resourceType) {
      this.resourceType = resourceType;
    }

    public ConsortiumSearchContextBuilder filter(String name, String value) {
      if (StringUtils.isNotBlank(name) && StringUtils.isNotBlank(value)) {
        this.filters.add(Pair.pair(name, value));
      }
      return this;
    }

    public ConsortiumSearchContextBuilder limit(Integer limit) {
      this.limit = limit;
      return this;
    }

    public ConsortiumSearchContextBuilder offset(Integer offset) {
      this.offset = offset;
      return this;
    }

    public ConsortiumSearchContextBuilder sortBy(String sortBy) {
      this.sortBy = sortBy;
      return this;
    }

    public ConsortiumSearchContextBuilder sortOrder(SortOrder sortOrder) {
      this.sortOrder = sortOrder;
      return this;
    }

    public ConsortiumSearchContext build() {
      return new ConsortiumSearchContext(this.resourceType, this.filters, this.limit, this.offset,
        this.sortBy, this.sortOrder);
    }
  }
}

