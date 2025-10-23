package org.folio.search.service.reindex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.util.List;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.exception.ReindexException;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

@UnitTest
@ExtendWith(MockitoExtension.class)
class StagingMigrationServiceTest {

  private static final String TENANT_ID = "test_tenant";

  @InjectMocks
  private StagingMigrationService stagingMigrationService;

  @Mock
  private JdbcTemplate jdbcTemplate;

  @Mock
  private FolioExecutionContext context;

  @Mock
  private ReindexCommonService reindexCommonService;

  @Mock
  private ReindexConfigurationProperties reindexConfigurationProperties;
  @Mock
  private org.folio.spring.FolioModuleMetadata folioModuleMetadata;

  @BeforeEach
  void setUp() {
    lenient().when(context.getTenantId()).thenReturn(TENANT_ID);
    lenient().when(context.getFolioModuleMetadata()).thenReturn(folioModuleMetadata);
    lenient().when(folioModuleMetadata.getDBSchemaName(TENANT_ID)).thenReturn("test_tenant_mod_search");
    lenient().when(reindexConfigurationProperties.getMigrationWorkMem()).thenReturn("64MB");
  }

  @Test
  void migrateAllStagingTables_fullReindex() {
    // Arrange
    when(jdbcTemplate.update(contains("staging_instance"), any(Timestamp.class))).thenReturn(10);
    when(jdbcTemplate.update(contains("staging_holding"))).thenReturn(15);
    when(jdbcTemplate.update(contains("staging_item"), any(Timestamp.class))).thenReturn(20);
    when(jdbcTemplate.update(contains("staging_subject"), any(Timestamp.class))).thenReturn(5);
    when(jdbcTemplate.update(contains("staging_contributor"), any(Timestamp.class))).thenReturn(8);
    when(jdbcTemplate.update(contains("staging_classification"), any(Timestamp.class))).thenReturn(3);
    when(jdbcTemplate.update(contains("staging_call_number"), any(Timestamp.class))).thenReturn(2);
    when(jdbcTemplate.update(contains("staging_instance_subject"))).thenReturn(12);
    when(jdbcTemplate.update(contains("staging_instance_contributor"))).thenReturn(14);
    when(jdbcTemplate.update(contains("staging_instance_classification"))).thenReturn(6);
    when(jdbcTemplate.update(contains("staging_instance_call_number"))).thenReturn(4);
    doNothing().when(jdbcTemplate).execute(anyString());

    // Act
    var result = stagingMigrationService.migrateAllStagingTables(null);

    // Assert - verify result metrics
    assertThat(result).isNotNull();
    assertThat(result.getDuration()).isGreaterThan(0);
    assertThat(result.getTotalInstances()).isEqualTo(10);
    assertThat(result.getTotalHoldings()).isEqualTo(15);
    assertThat(result.getTotalItems()).isEqualTo(20);
    // Total relationships: 5 + 8 + 3 + 2 + 12 + 14 + 6 + 4 = 54
    assertThat(result.getTotalRelationships()).isEqualTo(54);

    // Verify work_mem was set
    verify(jdbcTemplate).execute(contains("SET LOCAL work_mem"));

    // Verify all analyze statements were called
    verify(jdbcTemplate, times(11)).execute(contains("ANALYZE"));

    // Verify no deleteRecordsByTenantId was called for full reindex
    verifyNoInteractions(reindexCommonService);

    // Verify correct timestamp is used
    var expectedTimestamp = Timestamp.valueOf("2000-01-01 00:00:00");
    verify(jdbcTemplate, times(6)).update(anyString(), eq(expectedTimestamp));

    // Verify the order of operations
    var inOrder = inOrder(jdbcTemplate);

    // Phase 1: Setup
    inOrder.verify(jdbcTemplate).execute(contains("SET LOCAL work_mem"));
    inOrder.verify(jdbcTemplate, times(11)).execute(contains("ANALYZE"));

    // Phase 2: Instances (first main table)
    inOrder.verify(jdbcTemplate).update(contains("staging_instance"), any(Timestamp.class));

    // Phase 3: Holdings and Items
    inOrder.verify(jdbcTemplate).update(contains("staging_holding"));
    inOrder.verify(jdbcTemplate).update(contains("staging_item"), any(Timestamp.class));

    // Phase 4: Child resources
    inOrder.verify(jdbcTemplate).update(contains("staging_subject"), any(Timestamp.class));
    inOrder.verify(jdbcTemplate).update(contains("staging_contributor"), any(Timestamp.class));
    inOrder.verify(jdbcTemplate).update(contains("staging_classification"), any(Timestamp.class));
    inOrder.verify(jdbcTemplate).update(contains("staging_call_number"), any(Timestamp.class));

    // Phase 5: Relationships
    inOrder.verify(jdbcTemplate).update(contains("staging_instance_subject"));
    inOrder.verify(jdbcTemplate).update(contains("staging_instance_contributor"));
    inOrder.verify(jdbcTemplate).update(contains("staging_instance_classification"));
    inOrder.verify(jdbcTemplate).update(contains("staging_instance_call_number"));
  }

  @Test
  void migrateAllStagingTables_memberTenantReindex_shouldDeleteExistingTenantData() {
    // Arrange
    var targetTenantId = MEMBER_TENANT_ID;
    when(jdbcTemplate.update(anyString())).thenReturn(5);
    when(jdbcTemplate.update(anyString(), any(Timestamp.class))).thenReturn(5);
    doNothing().when(jdbcTemplate).execute(anyString());
    doNothing().when(reindexCommonService).deleteRecordsByTenantId(targetTenantId);

    // Act
    var result = stagingMigrationService.migrateAllStagingTables(targetTenantId);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getTotalInstances()).isEqualTo(5);
    assertThat(result.getTotalHoldings()).isEqualTo(5);
    assertThat(result.getTotalItems()).isEqualTo(5);

    // Verify pre-migration cleanup was called
    verify(reindexCommonService).deleteRecordsByTenantId(targetTenantId);

    // Verify order of operations
    var inOrder = inOrder(jdbcTemplate, reindexCommonService);
    inOrder.verify(jdbcTemplate).execute(contains("SET LOCAL work_mem"));
    inOrder.verify(jdbcTemplate, times(11)).execute(contains("ANALYZE"));
    inOrder.verify(reindexCommonService).deleteRecordsByTenantId(targetTenantId);
    inOrder.verify(jdbcTemplate).update(contains("staging_instance"), any(Timestamp.class));
  }

  @Test
  void migrateAllStagingTables_whenDatabaseError_shouldThrowReindexException() {
    // Arrange
    doThrow(new DataAccessException("Database error") {}).when(jdbcTemplate).execute(anyString());

    // Act & Assert
    assertThatThrownBy(() -> stagingMigrationService.migrateAllStagingTables(null))
      .isInstanceOf(ReindexException.class)
      .hasMessageContaining("Failed to migrate staging tables")
      .hasCauseInstanceOf(DataAccessException.class);
  }

  @Test
  void migrateAllStagingTables_whenMigrationFails_shouldThrowReindexException() {
    // Arrange
    doNothing().when(jdbcTemplate).execute(anyString());
    when(jdbcTemplate.update(anyString(), any(Timestamp.class)))
      .thenThrow(new DataAccessException("SQL error") {});

    // Act & Assert
    assertThatThrownBy(() -> stagingMigrationService.migrateAllStagingTables(null))
      .isInstanceOf(ReindexException.class)
      .hasMessageContaining("Failed to migrate staging tables");
  }

  @Test
  void setWorkMem_withInvalidFormat_shouldThrowReindexException() {
    // Arrange
    when(reindexConfigurationProperties.getMigrationWorkMem()).thenReturn("invalid_value");

    // Act & Assert
    assertThatThrownBy(() -> stagingMigrationService.migrateAllStagingTables(null))
      .isInstanceOf(ReindexException.class)
      .hasMessageContaining("Invalid work_mem format");
  }

  @Test
  void setWorkMem_withValidFormats_shouldSucceed() {
    // Test various valid formats
    var validFormats = List.of("64MB", "512KB", "1GB", "2048MB", "100KB");

    for (var format : validFormats) {
      when(reindexConfigurationProperties.getMigrationWorkMem()).thenReturn(format);
      when(jdbcTemplate.update(anyString())).thenReturn(0);
      when(jdbcTemplate.update(anyString(), any(Timestamp.class))).thenReturn(0);
      doNothing().when(jdbcTemplate).execute(anyString());

      // Should not throw exception
      stagingMigrationService.migrateAllStagingTables(null);

      verify(jdbcTemplate).execute("SET LOCAL work_mem = '" + format + "'");
    }
  }

  @Test
  void setWorkMem_whenSetFails_shouldThrowReindexException() {
    // Arrange
    when(reindexConfigurationProperties.getMigrationWorkMem()).thenReturn("64MB");
    doThrow(new DataAccessException("Cannot set work_mem") {})
      .when(jdbcTemplate).execute(contains("SET LOCAL work_mem"));

    // Act & Assert
    assertThatThrownBy(() -> stagingMigrationService.migrateAllStagingTables(null))
      .isInstanceOf(ReindexException.class)
      .hasMessageContaining("Failed to set work_mem");
  }

  @Test
  void cleanupStagingTables_shouldCallCleanupFunction() {
    // Arrange
    doNothing().when(jdbcTemplate).execute(anyString());

    // Act
    stagingMigrationService.cleanupStagingTables();

    // Assert
    verify(jdbcTemplate).execute(contains("cleanup_all_staging_tables()"));
  }

  @Test
  void cleanupStagingTables_whenFails_shouldPropagateException() {
    // Arrange
    doThrow(new DataAccessException("Cleanup failed") {})
      .when(jdbcTemplate).execute(anyString());

    // Act & Assert
    assertThatThrownBy(() -> stagingMigrationService.cleanupStagingTables())
      .isInstanceOf(DataAccessException.class);
  }
}

