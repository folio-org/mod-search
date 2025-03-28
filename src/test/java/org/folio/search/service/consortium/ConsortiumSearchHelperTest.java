package org.folio.search.service.consortium;

import static com.google.common.collect.Sets.newHashSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.model.types.ResourceType.INSTANCE;
import static org.folio.search.model.types.ResourceType.INSTANCE_CONTRIBUTOR;
import static org.folio.search.model.types.ResourceType.INSTANCE_SUBJECT;
import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.index.query.QueryBuilders.matchAllQuery;
import static org.opensearch.index.query.QueryBuilders.termQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.folio.search.model.index.InstanceSubResource;
import org.folio.search.model.index.SubjectResource;
import org.folio.search.model.service.BrowseContext;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.TermQueryBuilder;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ConsortiumSearchHelperTest {

  private static final String SUBRESOURCE_PREFIX = "instances.";
  private static final String TENANT_ID_FIELD_NAME = "tenantId";
  private static final String TENANT_ID_FIELD = SUBRESOURCE_PREFIX + TENANT_ID_FIELD_NAME;
  private static final String SHARED_FIELD_NAME = "shared";
  private static final String SHARED_FIELD = SUBRESOURCE_PREFIX + SHARED_FIELD_NAME;

  @Mock
  private FolioExecutionContext context;
  @Mock
  private ConsortiumTenantService consortiumTenantService;

  @Spy
  @InjectMocks
  private ConsortiumSearchHelper consortiumSearchHelper;

  @Test
  void filterQueryForActiveAffiliation_positive_basic() {
    var query = matchAllQuery();
    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(consortiumTenantService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(CENTRAL_TENANT_ID));

    consortiumSearchHelper.filterQueryForActiveAffiliation(query, INSTANCE);

    verify(consortiumSearchHelper).filterQueryForActiveAffiliation(query, TENANT_ID, CENTRAL_TENANT_ID, INSTANCE);
  }

  @Test
  void filterQueryForActiveAffiliation_positive_basicNotConsortiumTenant() {
    var query = matchAllQuery();
    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(consortiumTenantService.getCentralTenant(TENANT_ID)).thenReturn(Optional.empty());

    var actual = consortiumSearchHelper.filterQueryForActiveAffiliation(query, INSTANCE);

    assertThat(actual).isEqualTo(query);
    verify(consortiumSearchHelper, times(0)).filterQueryForActiveAffiliation(any(), any(), any(), any());
  }

  @Test
  void filterQueryActiveAffiliation_filteredByMemberTenant() {
    var query = boolQuery()
      .filter(termQuery("staffSuppress", false))
      .filter(termQuery(SHARED_FIELD_NAME, false));
    var expected = boolQuery()
      .filter(termQuery("staffSuppress", false))
      .filter(termQuery(SHARED_FIELD_NAME, false))
      .minimumShouldMatch(1)
      .should(termQuery(TENANT_ID_FIELD_NAME, TENANT_ID))
      .should(termQuery(SHARED_FIELD_NAME, true));

    when(consortiumTenantService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(CENTRAL_TENANT_ID));

    var actual = consortiumSearchHelper.filterQueryForActiveAffiliation(query, INSTANCE, TENANT_ID);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void filterQueryActiveAffiliation_notFilteredByMemberTenant() {
    var query = boolQuery()
      .filter(termQuery("staffSuppress", false))
      .filter(termQuery(SHARED_FIELD_NAME, false));
    var expected = boolQuery()
      .filter(termQuery("staffSuppress", false))
      .filter(termQuery(SHARED_FIELD_NAME, false))
      .minimumShouldMatch(1)
      .should(termQuery(SHARED_FIELD_NAME, true));

    when(consortiumTenantService.getCentralTenant(CENTRAL_TENANT_ID)).thenReturn(Optional.of(CENTRAL_TENANT_ID));

    var actual = consortiumSearchHelper.filterQueryForActiveAffiliation(query, INSTANCE, CENTRAL_TENANT_ID);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void filterQueryForActiveAffiliation_positive_basicConsortiumCentralTenant() {
    var query = matchAllQuery();
    var expected = boolQuery()
      .minimumShouldMatch(1)
      .should(termQuery("shared", true));

    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(consortiumTenantService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(TENANT_ID));

    var actual = consortiumSearchHelper.filterQueryForActiveAffiliation(query, INSTANCE);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void filterQueryForActiveAffiliation_positive_noPrefix() {
    var query = matchAllQuery();
    var expected = boolQuery()
      .minimumShouldMatch(1)
      .should(termQuery("tenantId", TENANT_ID))
      .should(termQuery("shared", true));

    var actual =
      consortiumSearchHelper.filterQueryForActiveAffiliation(query, TENANT_ID, CENTRAL_TENANT_ID, INSTANCE);

    assertThat(actual).isEqualTo(expected);
    verify(consortiumSearchHelper).filterQueryForActiveAffiliation(query, TENANT_ID, CENTRAL_TENANT_ID, INSTANCE);
  }

  @Test
  void filterQueryForActiveAffiliation_positive() {
    var query = matchAllQuery();
    var expected = boolQuery()
      .minimumShouldMatch(1)
      .should(termQuery(TENANT_ID_FIELD, TENANT_ID))
      .should(termQuery(SHARED_FIELD, true));

    var actual = consortiumSearchHelper.filterQueryForActiveAffiliation(query, TENANT_ID, CENTRAL_TENANT_ID,
      INSTANCE_CONTRIBUTOR);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void filterQueryForActiveAffiliation_positive_boolQuery() {
    var query = boolQuery();
    var expected = boolQuery()
      .minimumShouldMatch(1)
      .should(termQuery(TENANT_ID_FIELD, TENANT_ID))
      .should(termQuery(SHARED_FIELD, true));

    var actual = consortiumSearchHelper.filterQueryForActiveAffiliation(query, TENANT_ID,
      CENTRAL_TENANT_ID, INSTANCE_SUBJECT);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void filterQueryForActiveAffiliation_positive_boolQueryWithShould() {
    var query = boolQuery().should(termQuery("test", "test"));
    var expected = boolQuery()
      .minimumShouldMatch(1)
      .should(termQuery("test", "test"))
      .must(boolQuery()
        .should(termQuery(TENANT_ID_FIELD, TENANT_ID))
        .should(termQuery(SHARED_FIELD, true)));

    var actual = consortiumSearchHelper.filterQueryForActiveAffiliation(query, TENANT_ID,
      CENTRAL_TENANT_ID, INSTANCE_SUBJECT);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void filterQueryForActiveAffiliation_positive_otherQuery() {
    var query = termQuery("test", "test");
    var expected = boolQuery()
      .minimumShouldMatch(1)
      .must(termQuery("test", "test"))
      .should(termQuery(TENANT_ID_FIELD, TENANT_ID))
      .should(termQuery(SHARED_FIELD, true));

    var actual = consortiumSearchHelper.filterQueryForActiveAffiliation(query, TENANT_ID,
      CENTRAL_TENANT_ID, INSTANCE_CONTRIBUTOR);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void filterBrowseQueryForActiveAffiliation_positive_notConsortiumTenant() {
    var browseContext = browseContext(false, null);
    var query = matchAllQuery();

    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(consortiumTenantService.getCentralTenant(TENANT_ID)).thenReturn(Optional.empty());

    var actual = consortiumSearchHelper.filterBrowseQueryForActiveAffiliation(browseContext, query,
      INSTANCE);

    assertThat(actual).isEqualTo(query);
    assertThat(browseContext.getFilters()).isEmpty();
  }

  @Test
  void filterBrowseQueryForActiveAffiliation_positive_consortiumCentralTenant() {
    var browseContext = browseContext(false, null);
    var query = matchAllQuery();
    var expected = boolQuery()
      .must(termQuery(TENANT_ID_FIELD, TENANT_ID))
      .must(termQuery(SHARED_FIELD, false));

    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(consortiumTenantService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(TENANT_ID));

    var actual = consortiumSearchHelper.filterBrowseQueryForActiveAffiliation(browseContext, query,
      INSTANCE);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void filterBrowseQueryForActiveAffiliation_positive_shared() {
    var browseContext = browseContext(true, null);
    var query = matchAllQuery();
    var expected = boolQuery()
      .must(termQuery(SHARED_FIELD, true));

    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(consortiumTenantService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(CENTRAL_TENANT_ID));

    var actual = consortiumSearchHelper.filterBrowseQueryForActiveAffiliation(browseContext, query, INSTANCE);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void filterBrowseQueryForActiveAffiliation_positive_local() {
    var browseContext = browseContext(false, null);
    var query = matchAllQuery();
    var expected = boolQuery()
      .must(termQuery(TENANT_ID_FIELD, TENANT_ID))
      .must(termQuery(SHARED_FIELD, false));

    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(consortiumTenantService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(CENTRAL_TENANT_ID));

    var actual = consortiumSearchHelper.filterBrowseQueryForActiveAffiliation(browseContext, query,
      INSTANCE);

    assertThat(actual).isEqualTo(expected);
    assertThat(browseContext.getFilters()).isNotEmpty();
  }

  @Test
  void filterBrowseQueryForActiveAffiliation_positive_localWithShould() {
    var browseContext = browseContext(false, null);
    var query = boolQuery()
      .should(termQuery("test", "test"));
    var expected = boolQuery()
      .should(termQuery("test", "test"))
      .minimumShouldMatch(1)
      .must(termQuery(TENANT_ID_FIELD, TENANT_ID))
      .must(termQuery(SHARED_FIELD, false));

    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(consortiumTenantService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(CENTRAL_TENANT_ID));

    var actual = consortiumSearchHelper.filterBrowseQueryForActiveAffiliation(browseContext, query,
      INSTANCE);

    assertThat(actual).isEqualTo(expected);
    assertThat(browseContext.getFilters()).isNotEmpty();
  }

  @Test
  void filterSubResourcesForConsortium_positive_notConsortiumTenant() {
    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(consortiumTenantService.getCentralTenant(TENANT_ID)).thenReturn(Optional.empty());

    var browseContext = browseContext(false, null);
    var resource = new SubjectResource("id", "value", "authority", "source", "type", subResources());

    var actual = consortiumSearchHelper.filterSubResourcesForConsortium(browseContext, resource,
      SubjectResource::instances);

    assertThat(actual).isEqualTo(resource.instances());
  }

  @Test
  void filterSubResourcesForConsortium_positive_centralTenant() {
    when(context.getTenantId()).thenReturn(CENTRAL_TENANT_ID);
    when(consortiumTenantService.getCentralTenant(CENTRAL_TENANT_ID)).thenReturn(Optional.of(CENTRAL_TENANT_ID));

    var browseContext = browseContext(null, null);
    var resource = new SubjectResource("id", "value", "authority", "source", "type", subResources());

    var actual = consortiumSearchHelper.filterSubResourcesForConsortium(browseContext, resource,
      SubjectResource::instances);

    assertThat(actual).hasSize(2);
  }

  @Test
  void filterSubResourcesForConsortium_positive_centralTenant_withTenantFilter() {
    when(context.getTenantId()).thenReturn(CENTRAL_TENANT_ID);
    when(consortiumTenantService.getCentralTenant(CENTRAL_TENANT_ID)).thenReturn(Optional.of(CENTRAL_TENANT_ID));

    var browseContext = browseContext(null, TENANT_ID);
    var resource = new SubjectResource("id", "value", "authority", "source", "type", subResources());

    var actual = consortiumSearchHelper.filterSubResourcesForConsortium(browseContext, resource,
      SubjectResource::instances);

    assertThat(actual).hasSize(1);
  }

  @Test
  void filterSubResourcesForConsortium_positive_memberTenant() {
    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(consortiumTenantService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(CENTRAL_TENANT_ID));

    var browseContext = browseContext(null, "member");
    var resource = new SubjectResource("id", "value", "authority", "source", "type", subResources());

    var actual = consortiumSearchHelper.filterSubResourcesForConsortium(browseContext, resource,
      SubjectResource::instances);

    assertThat(actual).isEmpty();
  }

  @Test
  void filterSubResourcesForConsortium_positive_local() {
    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(consortiumTenantService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(CENTRAL_TENANT_ID));

    var browseContext = browseContext(false, null);
    var resource = new SubjectResource("id", "value", "authority", "source", "type", subResources());

    var actual = consortiumSearchHelper.filterSubResourcesForConsortium(browseContext, resource,
      SubjectResource::instances);

    assertThat(actual).hasSize(1)
      .allMatch(instanceSubResource -> instanceSubResource.getShared().equals(Boolean.FALSE))
      .allMatch(instanceSubResource -> instanceSubResource.getTenantId().equals(TENANT_ID));
  }

  @Test
  void filterSubResourcesForConsortium_positive_shared() {
    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(consortiumTenantService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(CENTRAL_TENANT_ID));

    var browseContext = browseContext(true, null);
    var subResources = subResources();
    var resource = new SubjectResource("id", "value", "authority", "source", "type", subResources);
    var expected = newHashSet(subResources);
    expected.removeIf(s -> !s.getShared());

    var actual = consortiumSearchHelper.filterSubResourcesForConsortium(browseContext, resource,
      SubjectResource::instances);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void getBrowseFilter_positive() {
    var filterKey = "filterKey";
    var expected = new TermQueryBuilder(filterKey, "test");
    var browseContext = browseContext(null, null);
    browseContext.getFilters().add(expected);

    var actual = consortiumSearchHelper.getBrowseFilter(browseContext, filterKey);

    assertThat(actual).contains(expected);
  }

  private BrowseContext browseContext(Boolean sharedFilter, String tenantFilter) {
    var browseContext = BrowseContext.builder();

    List<QueryBuilder> filters = new ArrayList<>();
    if (sharedFilter != null) {
      filters.add(termQuery(SHARED_FIELD, sharedFilter));
    }
    if (tenantFilter != null) {
      filters.add(termQuery(TENANT_ID_FIELD, tenantFilter));
    }

    return browseContext.filters(filters).build();
  }

  private Set<InstanceSubResource> subResources() {
    return Set.of(subResource(TENANT_ID, false),
      subResource(TENANT_ID, true),
      subResource(CENTRAL_TENANT_ID, false),
      subResource(CENTRAL_TENANT_ID, true));
  }

  private InstanceSubResource subResource(String tenantId, boolean shared) {
    return InstanceSubResource.builder().tenantId(tenantId).shared(shared).build();
  }
}
