package org.folio.search.service.consortium;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newHashSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.SearchUtils.CONTRIBUTOR_RESOURCE;
import static org.folio.search.utils.SearchUtils.INSTANCE_SUBJECT_RESOURCE;
import static org.folio.search.utils.TestConstants.CONSORTIUM_TENANT_ID;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.index.query.QueryBuilders.matchAllQuery;
import static org.opensearch.index.query.QueryBuilders.termQuery;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.folio.search.model.index.InstanceSubResource;
import org.folio.search.model.index.SubjectResource;
import org.folio.search.model.service.BrowseContext;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.test.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ConsortiumSearchHelperTest {

  private static final String SUBRESOURCE_PREFIX = "instances.";
  private static final String TENANT_ID_FIELD = SUBRESOURCE_PREFIX + "tenantId";
  private static final String SHARED_FIELD = SUBRESOURCE_PREFIX + "shared";

  @Mock
  private FolioExecutionContext context;
  @Mock
  private ConsortiumTenantService tenantService;

  @Spy
  @InjectMocks
  private ConsortiumSearchHelper consortiumSearchHelper;

  @Test
  void filterQueryForActiveAffiliation_positive_basic() {
    var query = matchAllQuery();
    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(tenantService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(CONSORTIUM_TENANT_ID));

    consortiumSearchHelper.filterQueryForActiveAffiliation(query, "resource");

    verify(consortiumSearchHelper).filterQueryForActiveAffiliation(query, TENANT_ID, CONSORTIUM_TENANT_ID, "resource");
  }

  @Test
  void filterQueryForActiveAffiliation_positive_basicNotConsortiumTenant() {
    var query = matchAllQuery();
    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(tenantService.getCentralTenant(TENANT_ID)).thenReturn(Optional.empty());

    var actual = consortiumSearchHelper.filterQueryForActiveAffiliation(query, "resource");

    assertThat(actual).isEqualTo(query);
    verify(consortiumSearchHelper, times(0)).filterQueryForActiveAffiliation(any(), any(), any(), any());
  }

  @Test
  void filterQueryForActiveAffiliation_positive_basicConsortiumCentralTenant() {
    var query = matchAllQuery();
    var expected = boolQuery()
      .minimumShouldMatch(1)
      .should(termQuery("shared", true));

    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(tenantService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(TENANT_ID));

    var actual = consortiumSearchHelper.filterQueryForActiveAffiliation(query, "resource");

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
      consortiumSearchHelper.filterQueryForActiveAffiliation(query, TENANT_ID, CONSORTIUM_TENANT_ID, "resource");

    assertThat(actual).isEqualTo(expected);
    verify(consortiumSearchHelper).filterQueryForActiveAffiliation(query, TENANT_ID, CONSORTIUM_TENANT_ID, "resource");
  }

  @Test
  void filterQueryForActiveAffiliation_positive() {
    var query = matchAllQuery();
    var expected = boolQuery()
      .minimumShouldMatch(1)
      .should(termQuery(TENANT_ID_FIELD, TENANT_ID))
      .should(termQuery(SHARED_FIELD, true));

    var actual = consortiumSearchHelper.filterQueryForActiveAffiliation(query, TENANT_ID, CONSORTIUM_TENANT_ID,
      CONTRIBUTOR_RESOURCE);

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
      CONSORTIUM_TENANT_ID, INSTANCE_SUBJECT_RESOURCE);

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
      CONSORTIUM_TENANT_ID, INSTANCE_SUBJECT_RESOURCE);

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
      CONSORTIUM_TENANT_ID, CONTRIBUTOR_RESOURCE);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void filterBrowseQueryForActiveAffiliation_positive_notConsortiumTenant() {
    var browseContext = browseContext(false);
    var query = matchAllQuery();

    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(tenantService.getCentralTenant(TENANT_ID)).thenReturn(Optional.empty());

    var actual = consortiumSearchHelper.filterBrowseQueryForActiveAffiliation(browseContext, query,
      "resource");

    assertThat(actual).isEqualTo(query);
    assertThat(browseContext.getFilters()).isEmpty();
  }

  @Test
  void filterBrowseQueryForActiveAffiliation_positive_consortiumCentralTenant() {
    var browseContext = browseContext(false);
    var query = matchAllQuery();
    var expected = boolQuery()
      .must(termQuery(TENANT_ID_FIELD, TENANT_ID));

    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(tenantService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(TENANT_ID));

    var actual = consortiumSearchHelper.filterBrowseQueryForActiveAffiliation(browseContext, query,
      "resource");

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void filterBrowseQueryForActiveAffiliation_positive_shared() {
    var browseContext = browseContext(true);
    var query = matchAllQuery();

    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(tenantService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(CONSORTIUM_TENANT_ID));

    consortiumSearchHelper.filterBrowseQueryForActiveAffiliation(browseContext, query, "resource");

    verify(consortiumSearchHelper).filterQueryForActiveAffiliation(query, TENANT_ID,
      CONSORTIUM_TENANT_ID, "resource");
  }

  @Test
  void filterBrowseQueryForActiveAffiliation_positive_local() {
    var browseContext = browseContext(false);
    var query = matchAllQuery();
    var expected = boolQuery()
      .must(termQuery(TENANT_ID_FIELD, TENANT_ID));

    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(tenantService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(CONSORTIUM_TENANT_ID));

    var actual = consortiumSearchHelper.filterBrowseQueryForActiveAffiliation(browseContext, query,
      "resource");

    assertThat(actual).isEqualTo(expected);
    assertThat(browseContext.getFilters()).isNotEmpty();
  }

  @Test
  void filterBrowseQueryForActiveAffiliation_positive_localWithShould() {
    var browseContext = browseContext(false);
    var query = boolQuery()
      .should(termQuery("test", "test"));
    var expected = boolQuery()
      .should(termQuery("test", "test"))
      .minimumShouldMatch(1)
      .must(termQuery(TENANT_ID_FIELD, TENANT_ID));

    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(tenantService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(CONSORTIUM_TENANT_ID));

    var actual = consortiumSearchHelper.filterBrowseQueryForActiveAffiliation(browseContext, query,
      "resource");

    assertThat(actual).isEqualTo(expected);
    assertThat(browseContext.getFilters()).isNotEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = TENANT_ID)
  @NullSource
  void filterSubResourcesForConsortium_positive_notConsortiumMemberTenant(String centralTenantId) {
    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(tenantService.getCentralTenant(TENANT_ID)).thenReturn(Optional.ofNullable(centralTenantId));

    var browseContext = browseContext(false);
    var resource = new SubjectResource();
    resource.setInstances(subResources());

    var actual = consortiumSearchHelper.filterSubResourcesForConsortium(browseContext, resource,
      SubjectResource::getInstances);

    assertThat(actual).isEqualTo(resource.getInstances());
  }

  @Test
  void filterSubResourcesForConsortium_positive_local() {
    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(tenantService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(CONSORTIUM_TENANT_ID));

    var browseContext = browseContext(false);
    var subResources = subResources();
    var resource = new SubjectResource();
    resource.setInstances(subResources);
    var expected = subResources.stream().filter(s -> s.getTenantId().equals(TENANT_ID)).collect(Collectors.toSet());

    var actual = consortiumSearchHelper.filterSubResourcesForConsortium(browseContext, resource,
      SubjectResource::getInstances);

    assertThat(actual).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(booleans = true)
  @NullSource
  void filterSubResourcesForConsortium_positive_shared(Boolean shared) {
    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(tenantService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(CONSORTIUM_TENANT_ID));

    var browseContext = browseContext(shared);
    var subResources = subResources();
    var resource = SubjectResource.builder().instances(subResources).build();
    var expected = newHashSet(subResources);
    expected.removeIf(s -> s.getTenantId().equals(CONSORTIUM_TENANT_ID) && !s.getShared());

    var actual = consortiumSearchHelper.filterSubResourcesForConsortium(browseContext, resource,
      SubjectResource::getInstances);

    assertThat(actual).isEqualTo(expected);
  }

  private BrowseContext browseContext(Boolean sharedFilter) {
    var browseContext = BrowseContext.builder();

    if (sharedFilter != null) {
      browseContext.filters(newArrayList(termQuery(SHARED_FIELD, sharedFilter)));
    }

    return browseContext.build();
  }

  private Set<InstanceSubResource> subResources() {
    return newHashSet(subResource(TENANT_ID, false),
      subResource(TENANT_ID, true),
      subResource(CONSORTIUM_TENANT_ID, false),
      subResource(CONSORTIUM_TENANT_ID, true));
  }

  private InstanceSubResource subResource(String tenantId, boolean shared) {
    return InstanceSubResource.builder().tenantId(tenantId).shared(shared).build();
  }
}
