package org.folio.search.service.reindex.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.folio.search.model.types.IndexFamilyStatus;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@UnitTest
@ExtendWith(MockitoExtension.class)
class IndexFamilyRepositoryTest {

  @Mock
  private JdbcTemplate jdbcTemplate;
  @Mock
  private FolioExecutionContext context;
  @Mock
  private FolioModuleMetadata folioModuleMetadata;

  private IndexFamilyRepository repository;

  @BeforeEach
  void setUp() {
    when(context.getTenantId()).thenReturn("test-tenant");
    when(context.getFolioModuleMetadata()).thenReturn(folioModuleMetadata);
    when(folioModuleMetadata.getDBSchemaName("test-tenant")).thenReturn("mod_search.test_tenant");
    repository = new IndexFamilyRepository(jdbcTemplate, context);
  }

  @Test
  void updateStatus_preservesExistingTimestamps() {
    var familyId = UUID.randomUUID();
    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    var statusCaptor = ArgumentCaptor.forClass(String.class);
    var activatedStatusCaptor = ArgumentCaptor.forClass(String.class);
    var retiredStatusCaptor = ArgumentCaptor.forClass(String.class);
    var idCaptor = ArgumentCaptor.forClass(UUID.class);

    repository.updateStatus(familyId, IndexFamilyStatus.RETIRING);

    verify(jdbcTemplate).update(sqlCaptor.capture(), statusCaptor.capture(), activatedStatusCaptor.capture(),
      retiredStatusCaptor.capture(), idCaptor.capture());
    assertThat(sqlCaptor.getValue()).contains("COALESCE(activated_at, CURRENT_TIMESTAMP)");
    assertThat(sqlCaptor.getValue()).contains("ELSE activated_at");
    assertThat(sqlCaptor.getValue()).contains("COALESCE(retired_at, CURRENT_TIMESTAMP)");
    assertThat(statusCaptor.getValue()).isEqualTo("RETIRING");
    assertThat(activatedStatusCaptor.getValue()).isEqualTo("RETIRING");
    assertThat(retiredStatusCaptor.getValue()).isEqualTo("RETIRING");
    assertThat(idCaptor.getValue()).isEqualTo(familyId);
  }

  @Test
  void updateRepresentation_reactivatesFamilyAndClearsRetiredTimestamp() {
    var familyId = UUID.randomUUID();
    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    var indexNameCaptor = ArgumentCaptor.forClass(String.class);
    var statusCaptor = ArgumentCaptor.forClass(String.class);
    var activatedStatusCaptor = ArgumentCaptor.forClass(String.class);
    var retiredStatusCaptor = ArgumentCaptor.forClass(String.class);
    var activeResetCaptor = ArgumentCaptor.forClass(String.class);
    var idCaptor = ArgumentCaptor.forClass(UUID.class);

    repository.updateRepresentation(familyId, "folio_instance_test", IndexFamilyStatus.ACTIVE);

    verify(jdbcTemplate).update(sqlCaptor.capture(), indexNameCaptor.capture(), statusCaptor.capture(),
      activatedStatusCaptor.capture(), retiredStatusCaptor.capture(), activeResetCaptor.capture(), idCaptor.capture());
    assertThat(sqlCaptor.getValue()).contains("SET index_name = ?");
    assertThat(sqlCaptor.getValue()).contains("WHEN ? IN ('ACTIVE', 'BUILDING') THEN NULL");
    assertThat(indexNameCaptor.getValue()).isEqualTo("folio_instance_test");
    assertThat(statusCaptor.getValue()).isEqualTo("ACTIVE");
    assertThat(activatedStatusCaptor.getValue()).isEqualTo("ACTIVE");
    assertThat(retiredStatusCaptor.getValue()).isEqualTo("ACTIVE");
    assertThat(activeResetCaptor.getValue()).isEqualTo("ACTIVE");
    assertThat(idCaptor.getValue()).isEqualTo(familyId);
  }

  @Test
  void updateRepresentation_buildingClearsRetiredTimestamp() {
    var familyId = UUID.randomUUID();
    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    var indexNameCaptor = ArgumentCaptor.forClass(String.class);
    var statusCaptor = ArgumentCaptor.forClass(String.class);
    var activatedStatusCaptor = ArgumentCaptor.forClass(String.class);
    var retiredStatusCaptor = ArgumentCaptor.forClass(String.class);
    var activeResetCaptor = ArgumentCaptor.forClass(String.class);
    var idCaptor = ArgumentCaptor.forClass(UUID.class);

    repository.updateRepresentation(familyId, "folio_instance_test", IndexFamilyStatus.BUILDING);

    verify(jdbcTemplate).update(sqlCaptor.capture(), indexNameCaptor.capture(), statusCaptor.capture(),
      activatedStatusCaptor.capture(), retiredStatusCaptor.capture(), activeResetCaptor.capture(), idCaptor.capture());
    assertThat(sqlCaptor.getValue()).contains("WHEN ? IN ('ACTIVE', 'BUILDING') THEN NULL");
    assertThat(indexNameCaptor.getValue()).isEqualTo("folio_instance_test");
    assertThat(statusCaptor.getValue()).isEqualTo("BUILDING");
    assertThat(activatedStatusCaptor.getValue()).isEqualTo("BUILDING");
    assertThat(retiredStatusCaptor.getValue()).isEqualTo("BUILDING");
    assertThat(activeResetCaptor.getValue()).isEqualTo("BUILDING");
    assertThat(idCaptor.getValue()).isEqualTo(familyId);
  }
}
