package org.folio.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import org.folio.search.service.reindex.jdbc.QueryVersionConfigRepository;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
  @Mock
  private QueryVersionConfigRepository configRepository;

  @InjectMocks
  private QueryVersionResolver resolver;

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
  void resolve_nullVersion_usesDefault() {
    when(configRepository.getDefaultVersion(TENANT)).thenReturn(Optional.of("1"));
    when(indexFamilyService.findActiveFamily(TENANT, QueryVersion.V1)).thenReturn(Optional.empty());
    when(indexNameProvider.getIndexName(ResourceType.INSTANCE, TENANT)).thenReturn("folio_instance_test_tenant");
    when(indexFamilyService.physicalIndexExists("folio_instance_test_tenant")).thenReturn(true);

    var result = resolver.resolve(null, TENANT);

    assertThat(result.indexName()).isEqualTo("folio_instance_test_tenant");
    assertThat(result.pathType()).isEqualTo(QueryResolution.PathType.LEGACY);
  }

  @Test
  void getDefaultVersion_returnsConfiguredValue() {
    when(configRepository.getDefaultVersion(TENANT)).thenReturn(Optional.of("2"));
    assertThat(resolver.getDefaultVersion(TENANT)).isEqualTo("2");
  }

  @Test
  void getDefaultVersion_defaultsTo1WhenNoConfig() {
    when(configRepository.getDefaultVersion(TENANT)).thenReturn(Optional.empty());
    assertThat(resolver.getDefaultVersion(TENANT)).isEqualTo("1");
  }

  @Test
  void setDefaultVersion_validatesAndUpserts_whenActiveFamilyExists() {
    var family = buildFamily(QueryVersion.V2, IndexFamilyStatus.ACTIVE);
    when(indexFamilyService.findActiveFamily(TENANT, QueryVersion.V2)).thenReturn(Optional.of(family));

    resolver.setDefaultVersion("2", TENANT);

    verify(configRepository).upsertDefaultVersion(TENANT, "2");
  }

  @Test
  void setDefaultVersion_v1AllowedWithLegacyPhysicalIndex() {
    when(indexFamilyService.findActiveFamily(TENANT, QueryVersion.V1)).thenReturn(Optional.empty());
    when(indexNameProvider.getIndexName(ResourceType.INSTANCE, TENANT)).thenReturn("folio_instance_test_tenant");
    when(indexFamilyService.physicalIndexExists("folio_instance_test_tenant")).thenReturn(true);

    resolver.setDefaultVersion("1", TENANT);

    verify(configRepository).upsertDefaultVersion(TENANT, "1");
  }

  @Test
  void setDefaultVersion_rejectsV2WithoutActiveFamily() {
    when(indexFamilyService.findActiveFamily(TENANT, QueryVersion.V2)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> resolver.setDefaultVersion("2", TENANT))
      .isInstanceOf(RequestValidationException.class)
      .hasMessageContaining("no ACTIVE family exists");
    verify(configRepository, never()).upsertDefaultVersion(TENANT, "2");
  }

  @Test
  void setDefaultVersion_rejectsV1WithoutFamilyOrLegacyIndex() {
    when(indexFamilyService.findActiveFamily(TENANT, QueryVersion.V1)).thenReturn(Optional.empty());
    when(indexNameProvider.getIndexName(ResourceType.INSTANCE, TENANT)).thenReturn("folio_instance_test_tenant");
    when(indexFamilyService.physicalIndexExists("folio_instance_test_tenant")).thenReturn(false);

    assertThatThrownBy(() -> resolver.setDefaultVersion("1", TENANT))
      .isInstanceOf(RequestValidationException.class)
      .hasMessageContaining("no ACTIVE family and no legacy index");
    verify(configRepository, never()).upsertDefaultVersion(TENANT, "1");
  }

  @Test
  void setDefaultVersion_throwsForInvalidVersion() {
    assertThatThrownBy(() -> resolver.setDefaultVersion("99", TENANT))
      .isInstanceOf(IllegalArgumentException.class);
  }

  private static IndexFamilyEntity buildFamily(QueryVersion version, IndexFamilyStatus status) {
    return new IndexFamilyEntity(UUID.randomUUID(), TENANT, 0, "index_0", status,
      Timestamp.from(Instant.now()), null, null, version);
  }
}
