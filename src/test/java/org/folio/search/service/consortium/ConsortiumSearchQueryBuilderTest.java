package org.folio.search.service.consortium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.SortOrder;
import org.folio.search.model.Pair;
import org.folio.search.model.service.ConsortiumSearchContext;
import org.folio.search.model.types.ResourceType;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.test.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ConsortiumSearchQueryBuilderTest {

  private @Mock FolioExecutionContext executionContext;

  @BeforeEach
  void setUp() {
    when(executionContext.getFolioModuleMetadata()).thenReturn(new FolioModuleMetadata() {
      @Override
      public String getModuleName() {
        return "module";
      }

      @Override
      public String getDBSchemaName(String tenantId) {
        return "schema";
      }
    });
  }

  @Test
  void testBuildSelectQuery_forHoldingsResource_whenAllParametersDefined() {
    var searchContext = new SearchContextMockBuilder().forHoldings().build();

    var actual = new ConsortiumSearchQueryBuilder(searchContext).buildSelectQuery(executionContext);
    assertEquals("SELECT i.instance_id as instanceId, i.tenant_id as tenantId, i.holdings ->> 'id' AS id, "
                 + "i.holdings ->> 'hrid' AS hrid, i.holdings ->> 'callNumberPrefix' AS callNumberPrefix, "
                 + "i.holdings ->> 'callNumber' AS callNumber, i.holdings ->> 'copyNumber' AS copyNumber, "
                 + "i.holdings ->> 'permanentLocationId' AS permanentLocationId, "
                 + "i.holdings ->> 'discoverySuppress' AS discoverySuppress "
                 + "FROM (SELECT instance_id, tenant_id, json_array_elements(json -> 'holdings') as holdings "
                 + "FROM schema.consortium_instance WHERE instance_id = ? AND tenant_id = ?) i "
                 + "ORDER BY id desc LIMIT 100 OFFSET 10", actual);
  }

  @NullAndEmptySource
  @ParameterizedTest
  void testBuildSelectQuery_forHoldingsResource_whenFiltersEmpty(String instanceId) {
    var searchContext = new SearchContextMockBuilder().forHoldings()
      .withInstanceId(instanceId).withTenantId(null).build();

    var actual = new ConsortiumSearchQueryBuilder(searchContext).buildSelectQuery(executionContext);
    assertEquals("SELECT i.instance_id as instanceId, i.tenant_id as tenantId, i.holdings ->> 'id' AS id, "
                 + "i.holdings ->> 'hrid' AS hrid, i.holdings ->> 'callNumberPrefix' AS callNumberPrefix, "
                 + "i.holdings ->> 'callNumber' AS callNumber, i.holdings ->> 'copyNumber' AS copyNumber, "
                 + "i.holdings ->> 'permanentLocationId' AS permanentLocationId, "
                 + "i.holdings ->> 'discoverySuppress' AS discoverySuppress "
                 + "FROM (SELECT instance_id, tenant_id, json_array_elements(json -> 'holdings') as holdings "
                 + "FROM schema.consortium_instance ) i "
                 + "ORDER BY id desc LIMIT 100 OFFSET 10", actual);
  }

  @NullAndEmptySource
  @ParameterizedTest
  void testBuildSelectQuery_forHoldingsResource_whenSortByEmpty(String sortBy) {
    var searchContext = new SearchContextMockBuilder().forHoldings().withSortBy(sortBy).build();

    var actual = new ConsortiumSearchQueryBuilder(searchContext).buildSelectQuery(executionContext);
    assertEquals("SELECT i.instance_id as instanceId, i.tenant_id as tenantId, i.holdings ->> 'id' AS id, "
                 + "i.holdings ->> 'hrid' AS hrid, i.holdings ->> 'callNumberPrefix' AS callNumberPrefix, "
                 + "i.holdings ->> 'callNumber' AS callNumber, i.holdings ->> 'copyNumber' AS copyNumber, "
                 + "i.holdings ->> 'permanentLocationId' AS permanentLocationId, "
                 + "i.holdings ->> 'discoverySuppress' AS discoverySuppress "
                 + "FROM (SELECT instance_id, tenant_id, json_array_elements(json -> 'holdings') as holdings "
                 + "FROM schema.consortium_instance WHERE instance_id = ? AND tenant_id = ?) i "
                 + "LIMIT 100 OFFSET 10", actual);
  }

  @Test
  void testBuildSelectQuery_forHoldingsResource_whenSortOrderEmpty() {
    var searchContext = new SearchContextMockBuilder().forHoldings().withSortOrder(null).build();

    var actual = new ConsortiumSearchQueryBuilder(searchContext).buildSelectQuery(executionContext);
    assertEquals("SELECT i.instance_id as instanceId, i.tenant_id as tenantId, i.holdings ->> 'id' AS id, "
                 + "i.holdings ->> 'hrid' AS hrid, i.holdings ->> 'callNumberPrefix' AS callNumberPrefix, "
                 + "i.holdings ->> 'callNumber' AS callNumber, i.holdings ->> 'copyNumber' AS copyNumber, "
                 + "i.holdings ->> 'permanentLocationId' AS permanentLocationId, "
                 + "i.holdings ->> 'discoverySuppress' AS discoverySuppress "
                 + "FROM (SELECT instance_id, tenant_id, json_array_elements(json -> 'holdings') as holdings "
                 + "FROM schema.consortium_instance WHERE instance_id = ? AND tenant_id = ?) i "
                 + "ORDER BY id LIMIT 100 OFFSET 10", actual);
  }

  @Test
  void testBuildSelectQuery_forHoldingsResource_whenLimitEmpty() {
    var searchContext = new SearchContextMockBuilder().forHoldings().withLimit(null).build();

    var actual = new ConsortiumSearchQueryBuilder(searchContext).buildSelectQuery(executionContext);
    assertEquals("SELECT i.instance_id as instanceId, i.tenant_id as tenantId, i.holdings ->> 'id' AS id, "
                 + "i.holdings ->> 'hrid' AS hrid, i.holdings ->> 'callNumberPrefix' AS callNumberPrefix, "
                 + "i.holdings ->> 'callNumber' AS callNumber, i.holdings ->> 'copyNumber' AS copyNumber, "
                 + "i.holdings ->> 'permanentLocationId' AS permanentLocationId, "
                 + "i.holdings ->> 'discoverySuppress' AS discoverySuppress "
                 + "FROM (SELECT instance_id, tenant_id, json_array_elements(json -> 'holdings') as holdings "
                 + "FROM schema.consortium_instance WHERE instance_id = ? AND tenant_id = ?) i "
                 + "ORDER BY id desc OFFSET 10", actual);
  }

  @Test
  void testBuildSelectQuery_forHoldingsResource_whenOffsetEmpty() {
    var searchContext = new SearchContextMockBuilder().forHoldings().withOffset(null).build();

    var actual = new ConsortiumSearchQueryBuilder(searchContext).buildSelectQuery(executionContext);
    assertEquals("SELECT i.instance_id as instanceId, i.tenant_id as tenantId, i.holdings ->> 'id' AS id, "
                 + "i.holdings ->> 'hrid' AS hrid, i.holdings ->> 'callNumberPrefix' AS callNumberPrefix, "
                 + "i.holdings ->> 'callNumber' AS callNumber, i.holdings ->> 'copyNumber' AS copyNumber, "
                 + "i.holdings ->> 'permanentLocationId' AS permanentLocationId, "
                 + "i.holdings ->> 'discoverySuppress' AS discoverySuppress "
                 + "FROM (SELECT instance_id, tenant_id, json_array_elements(json -> 'holdings') as holdings "
                 + "FROM schema.consortium_instance WHERE instance_id = ? AND tenant_id = ?) i "
                 + "ORDER BY id desc LIMIT 100", actual);
  }

  private static final class SearchContextMockBuilder {
    private ResourceType resourceType;
    private String instanceId = "inst123";
    private String tenantId = "tenant";
    private String sortBy = "id";
    private SortOrder sortOrder = SortOrder.DESC;
    private Integer limit = 100;
    private Integer offset = 10;

    SearchContextMockBuilder forHoldings() {
      this.resourceType = ResourceType.HOLDINGS;
      return this;
    }

    SearchContextMockBuilder withInstanceId(String instanceId) {
      this.instanceId = instanceId;
      return this;
    }

    SearchContextMockBuilder withTenantId(String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    SearchContextMockBuilder withSortBy(String sortBy) {
      this.sortBy = sortBy;
      return this;
    }

    SearchContextMockBuilder withSortOrder(SortOrder sortOrder) {
      this.sortOrder = sortOrder;
      return this;
    }

    SearchContextMockBuilder withLimit(Integer limit) {
      this.limit = limit;
      return this;
    }

    SearchContextMockBuilder withOffset(Integer offset) {
      this.offset = offset;
      return this;
    }

    ConsortiumSearchContext build() {
      ConsortiumSearchContext searchContext = mock(ConsortiumSearchContext.class);
      lenient().when(searchContext.getResourceType()).thenReturn(this.resourceType);

      lenient().when(searchContext.getFilters()).thenReturn(getFilters());
      lenient().when(searchContext.getSortBy()).thenReturn(this.sortBy);
      lenient().when(searchContext.getSortOrder()).thenReturn(this.sortOrder);
      lenient().when(searchContext.getLimit()).thenReturn(this.limit);
      lenient().when(searchContext.getOffset()).thenReturn(this.offset);
      return searchContext;
    }

    private ArrayList<Pair<String, String>> getFilters() {
      var filters = new ArrayList<Pair<String, String>>();
      if (StringUtils.isNotBlank(instanceId)) {
        filters.add(Pair.pair("instanceId", instanceId));
      }
      if (StringUtils.isNotBlank(tenantId)) {
        filters.add(Pair.pair("tenantId", tenantId));
      }
      return filters;
    }
  }
}
