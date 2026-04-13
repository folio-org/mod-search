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
    lenient().when(reindexConfigurationProperties.getMigrationStatementTimeout()).thenReturn("0");
  }

  @Test
  @SuppressWarnings("checkstyle:MethodLength")
  void migrateAllStagingTables_shouldMigrateAllPhasesInOrder() {
    // Arrange
    var targetTenantId = MEMBER_TENANT_ID;
    var expectedTimestamp = Timestamp.valueOf("2000-01-01 00:00:00");
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
    doNothing().when(reindexCommonService).deleteRecordsByTenantId(targetTenantId);

    // Act
    var result = stagingMigrationService.migrateAllStagingTables(targetTenantId);

    // Assert - verify result metrics
    assertThat(result).isNotNull();
    assertThat(result.getDuration()).isGreaterThan(0);
    assertThat(result.getTotalInstances()).isEqualTo(10);
    assertThat(result.getTotalHoldings()).isEqualTo(15);
    assertThat(result.getTotalItems()).isEqualTo(20);
    // Total relationships: 5 + 8 + 3 + 2 + 12 + 14 + 6 + 4 = 54
    assertThat(result.getTotalRelationships()).isEqualTo(54);

    // Verify the order of all operations
    var inOrder = inOrder(jdbcTemplate, reindexCommonService);

    // Phase 1: Transaction setup
    inOrder.verify(jdbcTemplate).execute(contains("SET LOCAL work_mem"));
    inOrder.verify(jdbcTemplate).execute(contains("SET LOCAL statement_timeout"));

    // Phase 2: Analyze staging tables
    inOrder.verify(jdbcTemplate, times(11)).execute(contains("ANALYZE"));

    // Phase 3: Pre-migration - clear existing tenant data from main tables
    inOrder.verify(reindexCommonService).deleteRecordsByTenantId(targetTenantId);

    // Phase 4: Migrate main entities
    inOrder.verify(jdbcTemplate).update(contains("staging_instance"), eq(expectedTimestamp));
    inOrder.verify(jdbcTemplate).update(contains("staging_holding"));
    inOrder.verify(jdbcTemplate).update(contains("staging_item"), eq(expectedTimestamp));

    // Phase 5: Migrate child resources (all use timestamp parameter)
    inOrder.verify(jdbcTemplate).update(contains("staging_subject"), eq(expectedTimestamp));
    inOrder.verify(jdbcTemplate).update(contains("staging_contributor"), eq(expectedTimestamp));
    inOrder.verify(jdbcTemplate).update(contains("staging_classification"), eq(expectedTimestamp));
    inOrder.verify(jdbcTemplate).update(contains("staging_call_number"), eq(expectedTimestamp));

    // Phase 6: Migrate relationships
    inOrder.verify(jdbcTemplate).update(contains("staging_instance_subject"));
    inOrder.verify(jdbcTemplate).update(contains("staging_instance_contributor"));
    inOrder.verify(jdbcTemplate).update(contains("staging_instance_classification"));
    inOrder.verify(jdbcTemplate).update(contains("staging_instance_call_number"));

    // Phase 7: Post-migration - truncate staging tables
    inOrder.verify(reindexCommonService).deleteAllRecords(targetTenantId);
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

    // Staging tables should NOT be truncated when migration fails
    verify(reindexCommonService, times(0)).deleteAllRecords(any());
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

    // Staging tables should NOT be truncated when migration fails
    verify(reindexCommonService, times(0)).deleteAllRecords(any());
  }

  @Test
  void configureTransactionSettings_withInvalidWorkMemFormat_shouldThrowReindexException() {
    // Arrange
    when(reindexConfigurationProperties.getMigrationWorkMem()).thenReturn("invalid_value");

    // Act & Assert
    assertThatThrownBy(() -> stagingMigrationService.migrateAllStagingTables(null))
      .isInstanceOf(ReindexException.class)
      .hasMessageContaining("Invalid work_mem format");
  }

  @Test
  void configureTransactionSettings_withInvalidStatementTimeoutFormat_shouldThrowReindexException() {
    // Arrange
    when(reindexConfigurationProperties.getMigrationWorkMem()).thenReturn("64MB");
    when(reindexConfigurationProperties.getMigrationStatementTimeout()).thenReturn("invalid_value");

    // Act & Assert
    assertThatThrownBy(() -> stagingMigrationService.migrateAllStagingTables(null))
      .isInstanceOf(ReindexException.class)
      .hasMessageContaining("Invalid statement_timeout format");
  }

  @Test
  void configureTransactionSettings_withValidFormats_shouldSucceed() {
    // Test various valid formats
    var validFormats = List.of("64MB", "512KB", "1GB", "2048MB", "100KB");

    for (var format : validFormats) {
      when(reindexConfigurationProperties.getMigrationWorkMem()).thenReturn(format);
      when(reindexConfigurationProperties.getMigrationStatementTimeout()).thenReturn("0");
      when(jdbcTemplate.update(anyString())).thenReturn(0);
      when(jdbcTemplate.update(anyString(), any(Timestamp.class))).thenReturn(0);
      doNothing().when(jdbcTemplate).execute(anyString());

      // Should not throw exception
      stagingMigrationService.migrateAllStagingTables(null);

      verify(jdbcTemplate).execute("SET LOCAL work_mem = '" + format + "'");
    }

    verify(jdbcTemplate, times(validFormats.size())).execute("SET LOCAL statement_timeout = '0'");
  }

  @Test
  void configureTransactionSettings_whenSetFails_shouldThrowReindexException() {
    // Arrange
    when(reindexConfigurationProperties.getMigrationWorkMem()).thenReturn("64MB");
    when(reindexConfigurationProperties.getMigrationStatementTimeout()).thenReturn("0");
    doThrow(new DataAccessException("Cannot set work_mem") {})
      .when(jdbcTemplate).execute(contains("SET LOCAL work_mem"));

    // Act & Assert
    assertThatThrownBy(() -> stagingMigrationService.migrateAllStagingTables(null))
      .isInstanceOf(ReindexException.class)
      .hasMessageContaining("Failed to configure transaction settings");
  }
}

