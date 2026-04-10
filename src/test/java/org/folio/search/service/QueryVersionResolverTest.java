package org.folio.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.reindex.IndexFamilyEntity;
import org.folio.search.model.service.QueryResolution;
import org.folio.search.model.types.IndexFamilyStatus;
import org.folio.search.model.types.QueryVersion;
import org.folio.search.model.types.ResourceType;
import org.folio.search.repository.IndexNameProvider;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class QueryVersionResolverTest {

  private static final String TENANT = "test_tenant";

  @Mock
  private IndexFamilyService indexFamilyService;
  @Mock
  private IndexNameProvider indexNameProvider;

  private QueryVersionResolver resolver;

  @BeforeEach
  void setUp() {
    resolver = new QueryVersionResolver(indexFamilyService, indexNameProvider, "1");
  }

  @Test
  void resolve_v1WithActiveFamily_returnsAliasWithLegacyPath() {
    var family = buildFamily(QueryVersion.V1, IndexFamilyStatus.ACTIVE);
    when(indexFamilyService.findActiveFamily(TENANT, QueryVersion.V1)).thenReturn(Optional.of(family));
    when(indexFamilyService.getAliasName(TENANT, QueryVersion.V1)).thenReturn("folio_instance_test_tenant");

    var result = resolver.resolve("1", TENANT);

    assertThat(result.indexName()).isEqualTo("folio_instance_test_tenant");
    assertThat(result.pathType()).isEqualTo(QueryResolution.PathType.LEGACY);
  }

  @Test
  void resolve_v2WithActiveFamily_returnsAliasWithFlatPath() {
    var family = buildFamily(QueryVersion.V2, IndexFamilyStatus.ACTIVE);
    when(indexFamilyService.findActiveFamily(TENANT, QueryVersion.V2)).thenReturn(Optional.of(family));
    when(indexFamilyService.getAliasName(TENANT, QueryVersion.V2)).thenReturn("folio_instance_search_test_tenant");

    var result = resolver.resolve("2", TENANT);

    assertThat(result.indexName()).isEqualTo("folio_instance_search_test_tenant");
    assertThat(result.pathType()).isEqualTo(QueryResolution.PathType.FLAT);
  }

  @Test
  void resolve_v1WithoutFamily_fallsBackToLegacyIndex() {
    when(indexFamilyService.findActiveFamily(TENANT, QueryVersion.V1)).thenReturn(Optional.empty());
    when(indexNameProvider.getIndexName(ResourceType.INSTANCE, TENANT)).thenReturn("folio_instance_test_tenant");
    when(indexFamilyService.physicalIndexExists("folio_instance_test_tenant")).thenReturn(true);

    var result = resolver.resolve("1", TENANT);

    assertThat(result.indexName()).isEqualTo("folio_instance_test_tenant");
    assertThat(result.pathType()).isEqualTo(QueryResolution.PathType.LEGACY);
  }

  @Test
  void resolve_v2WithoutFamily_throws() {
    when(indexFamilyService.findActiveFamily(TENANT, QueryVersion.V2)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> resolver.resolve("2", TENANT))
      .isInstanceOf(RequestValidationException.class)
      .hasMessageContaining("No ACTIVE index family for version 2");
  }

  @Test
  void resolve_nullVersion_usesEnvDefault() {
    when(indexFamilyService.findActiveFamily(TENANT, QueryVersion.V1)).thenReturn(Optional.empty());
    when(indexNameProvider.getIndexName(ResourceType.INSTANCE, TENANT)).thenReturn("folio_instance_test_tenant");
    when(indexFamilyService.physicalIndexExists("folio_instance_test_tenant")).thenReturn(true);

    var result = resolver.resolve(null, TENANT);

    assertThat(result.indexName()).isEqualTo("folio_instance_test_tenant");
    assertThat(result.pathType()).isEqualTo(QueryResolution.PathType.LEGACY);
  }

  @Test
  void resolve_nullVersion_honoursConfiguredDefaultV2() {
    var v2Resolver = new QueryVersionResolver(indexFamilyService, indexNameProvider, "2");
    var family = buildFamily(QueryVersion.V2, IndexFamilyStatus.ACTIVE);
    when(indexFamilyService.findActiveFamily(TENANT, QueryVersion.V2)).thenReturn(Optional.of(family));
    when(indexFamilyService.getAliasName(TENANT, QueryVersion.V2)).thenReturn("folio_instance_search_test_tenant");

    var result = v2Resolver.resolve(null, TENANT);

    assertThat(result.indexName()).isEqualTo("folio_instance_search_test_tenant");
    assertThat(result.pathType()).isEqualTo(QueryResolution.PathType.FLAT);
  }

  @Test
  void getDefaultVersion_returnsInjectedValue() {
    assertThat(resolver.getDefaultVersion()).isEqualTo("1");

    var v2Resolver = new QueryVersionResolver(indexFamilyService, indexNameProvider, "2");
    assertThat(v2Resolver.getDefaultVersion()).isEqualTo("2");
  }

  @Test
  void resolveVersion_returnsExplicitWhenProvided() {
    assertThat(resolver.resolveVersion("2")).isEqualTo("2");
  }

  @Test
  void resolveVersion_returnsDefaultWhenNull() {
    assertThat(resolver.resolveVersion(null)).isEqualTo("1");
  }

  private static IndexFamilyEntity buildFamily(QueryVersion version, IndexFamilyStatus status) {
    return new IndexFamilyEntity(UUID.randomUUID(), 0, "index_0", status,
      Timestamp.from(Instant.now()), null, null, version);
  }
}
