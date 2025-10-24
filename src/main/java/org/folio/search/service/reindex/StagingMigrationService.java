package org.folio.search.service.reindex;

import static org.folio.search.utils.JdbcUtils.getSchemaName;

import java.sql.Timestamp;
import java.util.regex.Pattern;
import lombok.extern.log4j.Log4j2;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.exception.ReindexException;
import org.folio.search.model.reindex.MigrationResult;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
public class StagingMigrationService {

  protected static final Timestamp RESOURCE_REINDEX_TIMESTAMP = Timestamp.valueOf("2000-01-01 00:00:00");
  private static final Pattern WORK_MEM_PATTERN = Pattern.compile("^\\d+\\s*(KB|MB|GB)$");

  private final JdbcTemplate jdbcTemplate;
  private final FolioExecutionContext context;
  private final ReindexCommonService reindexCommonService;
  private final ReindexConfigurationProperties reindexConfigurationProperties;

  public StagingMigrationService(JdbcTemplate jdbcTemplate,
                                 FolioExecutionContext context,
                                 ReindexCommonService reindexCommonService,
                                 ReindexConfigurationProperties reindexConfigurationProperties) {
    this.jdbcTemplate = jdbcTemplate;
    this.context = context;
    this.reindexCommonService = reindexCommonService;
    this.reindexConfigurationProperties = reindexConfigurationProperties;
  }

  @Transactional
  public MigrationResult migrateAllStagingTables(String targetTenantId) {
    var isMemberTenantRefresh = targetTenantId != null;
    var result = new MigrationResult();
    var startTime = System.currentTimeMillis();

    try {
      // Set work_mem for this transaction to optimize query performance
      setWorkMem();

      log.info("migrateAllStagingTables:: Starting migration for: [targetTenantId: {}]", targetTenantId);
      
      // Analyze staging tables for better query performance
      analyzeStagingTables();

      // Handle member tenant specific operations before main migration
      if (isMemberTenantRefresh) {
        handleMemberTenantPreMigration(targetTenantId);
      }

      // Execute the main data migration phases
      executeMainMigrationPhases(result);

      var duration = System.currentTimeMillis() - startTime;
      result.setDuration(duration);

      log.info("migrateAllStagingTables:: Migration complete in {} ms: {} for targetTenantId: {}",
        duration, result, targetTenantId);
      return result;
    } catch (ReindexException ex) {
      log.error("migrateAllStagingTables:: Migration failed due to reindex exception for targetTenantId {}",
        targetTenantId, ex);
      var message = "Failed to migrate staging tables: " + ex.getMessage();
      throw new ReindexException(message, ex.getCause());
    } catch (Exception e) {
      log.error("migrateAllStagingTables:: Migration failed for targetTenantId {}", targetTenantId, e);
      throw new ReindexException("Failed to migrate staging tables", e);
    }
  }

  private void handleMemberTenantPreMigration(String targetTenantId) {
    // For member tenant refresh: simply delete existing data for this tenant from main tables
    log.info("handleMemberTenantPreMigration:: Clearing existing tenant data from main tables for tenant: {}",
      targetTenantId);
    reindexCommonService.deleteRecordsByTenantId(targetTenantId);
    log.info("handleMemberTenantPreMigration:: Main table cleanup completed for tenant: {}", targetTenantId);
  }

  private void executeMainMigrationPhases(MigrationResult result) {
    // Phase 1: Instances
    log.info("executeMainMigrationPhases:: Starting instances migration...");
    migrateInstances(result);
    log.info("executeMainMigrationPhases:: Instances migration completed");

    // Phase 2: Holdings and Items
    log.info("executeMainMigrationPhases:: Starting holdings/items migration...");
    migrateHoldings(result);
    log.info("executeMainMigrationPhases:: Holdings migration completed");

    migrateItems(result);
    log.info("executeMainMigrationPhases:: Items migration completed");

    // Phase 3: Child resources (subjects, contributors, classifications, call numbers)
    log.info("executeMainMigrationPhases:: Starting child resources migration...");
    migrateSubjects(result);
    log.info("executeMainMigrationPhases:: Subject migration completed");

    migrateContributors(result);
    log.info("executeMainMigrationPhases:: Contributor migration completed");

    migrateClassifications(result);
    log.info("executeMainMigrationPhases:: Classification migration completed");

    migrateCallNumbers(result);
    log.info("executeMainMigrationPhases:: Call number migration completed");

    // Phase 4: Instance/item relationships
    log.info("executeMainMigrationPhases:: Starting instance relationships migration...");
    migrateInstanceSubjects(result);
    log.info("executeMainMigrationPhases:: Instance-subject migration completed");

    migrateInstanceContributors(result);
    log.info("executeMainMigrationPhases:: Instance-contributor migration complete");

    migrateInstanceClassifications(result);
    log.info("executeMainMigrationPhases:: Instance-classification migration completed");

    migrateInstanceCallNumbers(result);
    log.info("executeMainMigrationPhases:: Instance-call numbers migration completed");
  }

  private void migrateInstances(MigrationResult result) {
    var schema = getSchemaName(context);
    var sql = String.format("""
        INSERT INTO %s.instance (id, tenant_id, shared, is_bound_with, json, last_updated_date)
        SELECT id, tenant_id, shared, is_bound_with, json, ?
        FROM %s.staging_instance
        ORDER BY inserted_at DESC
        ON CONFLICT (id, tenant_id) DO UPDATE SET
            shared = EXCLUDED.shared,
            is_bound_with = EXCLUDED.is_bound_with,
            json = EXCLUDED.json,
            last_updated_date = EXCLUDED.last_updated_date
        """, schema, schema);

    var recordsUpserted = jdbcTemplate.update(sql, RESOURCE_REINDEX_TIMESTAMP);
    result.setTotalInstances(recordsUpserted);

    log.debug("migrateInstances:: Instance upserted: {} records", recordsUpserted);
  }

  private void migrateHoldings(MigrationResult result) {
    var schema = getSchemaName(context);
    var sql = String.format("""
        INSERT INTO %s.holding (id, tenant_id, instance_id, json)
        SELECT id, tenant_id, instance_id, json
        FROM %s.staging_holding
        ORDER BY inserted_at DESC
        ON CONFLICT (id, tenant_id) DO UPDATE SET
            instance_id = EXCLUDED.instance_id,
            json = EXCLUDED.json
        """, schema, schema);

    var recordsUpserted = jdbcTemplate.update(sql);
    result.setTotalHoldings(recordsUpserted);

    log.debug("migrateHoldings:: Holding upserted: {} records", recordsUpserted);
  }

  private void migrateItems(MigrationResult result) {
    var schema = getSchemaName(context);
    var sql = String.format("""
        INSERT INTO %s.item (id, tenant_id, instance_id, holding_id, json, last_updated_date)
        SELECT id, tenant_id, instance_id, holding_id, json, ?
        FROM %s.staging_item
        ORDER BY inserted_at DESC
        ON CONFLICT (id, tenant_id) DO UPDATE SET
            instance_id = EXCLUDED.instance_id,
            holding_id = EXCLUDED.holding_id,
            json = EXCLUDED.json,
            last_updated_date = EXCLUDED.last_updated_date
        """, schema, schema);

    var recordsUpserted = jdbcTemplate.update(sql, RESOURCE_REINDEX_TIMESTAMP);
    result.setTotalItems(recordsUpserted);

    log.debug("migrateItems:: Item upserted: {} records", recordsUpserted);
  }

  private void migrateInstanceSubjects(MigrationResult result) {
    var schema = getSchemaName(context);
    var sql = String.format("""
        INSERT INTO %s.instance_subject (instance_id, subject_id, tenant_id, shared)
        SELECT instance_id, subject_id, tenant_id, shared
        FROM %s.staging_instance_subject
        ORDER BY inserted_at DESC
        ON CONFLICT (subject_id, instance_id, tenant_id) DO NOTHING
        """, schema, schema);

    var recordsUpserted = jdbcTemplate.update(sql);
    result.setTotalRelationships(result.getTotalRelationships() + recordsUpserted);

    log.debug("migrateInstanceSubjects:: Instance-subject relationship upserted: {} records", recordsUpserted);
  }

  private void migrateInstanceContributors(MigrationResult result) {
    var schema = getSchemaName(context);
    var sql = String.format("""
        INSERT INTO %s.instance_contributor (instance_id, contributor_id, type_id, tenant_id, shared)
        SELECT instance_id, contributor_id, type_id, tenant_id, shared
        FROM %s.staging_instance_contributor
        ORDER BY inserted_at DESC
        ON CONFLICT (contributor_id, instance_id, type_id, tenant_id) DO NOTHING
        """, schema, schema);

    var recordsUpserted = jdbcTemplate.update(sql);
    result.setTotalRelationships(result.getTotalRelationships() + recordsUpserted);

    log.debug("migrateInstanceContributors:: Instance-contributor relationship upserted: {} records", recordsUpserted);
  }

  private void migrateInstanceClassifications(MigrationResult result) {
    var schema = getSchemaName(context);
    var sql = String.format("""
        INSERT INTO %s.instance_classification (instance_id, classification_id, tenant_id, shared)
        SELECT instance_id, classification_id, tenant_id, shared
        FROM %s.staging_instance_classification
        ORDER BY inserted_at DESC
        ON CONFLICT (classification_id, instance_id, tenant_id) DO NOTHING
        """, schema, schema);

    var recordsUpserted = jdbcTemplate.update(sql);
    result.setTotalRelationships(result.getTotalRelationships() + recordsUpserted);

    log.debug("migrateInstanceClassifications:: Instance-classification relationship upserted: {} records",
      recordsUpserted);
  }

  private void migrateInstanceCallNumbers(MigrationResult result) {
    var schema = getSchemaName(context);
    var sql = String.format("""
        INSERT INTO %s.instance_call_number (call_number_id, item_id, instance_id, tenant_id, location_id)
        SELECT call_number_id, item_id, instance_id, tenant_id, location_id
        FROM %s.staging_instance_call_number
        ORDER BY inserted_at DESC
        ON CONFLICT (call_number_id, item_id, instance_id, tenant_id) DO NOTHING
        """, schema, schema);

    var recordsUpserted = jdbcTemplate.update(sql);
    result.setTotalRelationships(result.getTotalRelationships() + recordsUpserted);

    log.debug("migrateInstanceCallNumbers:: Instance-call number relationship upserted: {} records", recordsUpserted);
  }

  private void migrateSubjects(MigrationResult result) {
    var schema = getSchemaName(context);
    var sql = String.format("""
        INSERT INTO %s.subject (id, value, authority_id, source_id, type_id, last_updated_date)
        SELECT id, value, authority_id, source_id, type_id, ?
        FROM %s.staging_subject
        ON CONFLICT (id) DO UPDATE
        SET last_updated_date = EXCLUDED.last_updated_date
        """, schema, schema);

    var recordsUpserted = jdbcTemplate.update(sql, RESOURCE_REINDEX_TIMESTAMP);
    result.setTotalRelationships(result.getTotalRelationships() + recordsUpserted);

    log.debug("migrateSubjects:: Subject upserted: {} records", recordsUpserted);
  }

  private void migrateContributors(MigrationResult result) {
    var schema = getSchemaName(context);
    var sql = String.format("""
        INSERT INTO %s.contributor (id, name, name_type_id, authority_id, last_updated_date)
        SELECT id, name, name_type_id, authority_id, ?
        FROM %s.staging_contributor
        ON CONFLICT (id) DO UPDATE
        SET last_updated_date = EXCLUDED.last_updated_date
        """, schema, schema);

    var recordsUpserted = jdbcTemplate.update(sql, RESOURCE_REINDEX_TIMESTAMP);
    result.setTotalRelationships(result.getTotalRelationships() + recordsUpserted);

    log.debug("migrateContributors:: Contributor upserted: {} records", recordsUpserted);
  }

  private void migrateClassifications(MigrationResult result) {
    var schema = getSchemaName(context);
    var sql = String.format("""
        INSERT INTO %s.classification (id, number, type_id, last_updated_date)
        SELECT id, number, type_id, ?
        FROM %s.staging_classification
        ON CONFLICT (id) DO UPDATE
        SET last_updated_date = EXCLUDED.last_updated_date
        """, schema, schema);

    var recordsUpserted = jdbcTemplate.update(sql, RESOURCE_REINDEX_TIMESTAMP);
    result.setTotalRelationships(result.getTotalRelationships() + recordsUpserted);

    log.debug("migrateClassifications:: Classification upserted: {} records", recordsUpserted);
  }

  private void migrateCallNumbers(MigrationResult result) {
    var schema = getSchemaName(context);
    var sql = String.format("""
        INSERT INTO %s.call_number
        (id, call_number, call_number_prefix, call_number_suffix, call_number_type_id, last_updated_date)
        SELECT id, call_number, call_number_prefix, call_number_suffix, call_number_type_id, ?
        FROM %s.staging_call_number
        ON CONFLICT (id) DO UPDATE
        SET last_updated_date = EXCLUDED.last_updated_date
        """, schema, schema);

    var recordsUpserted = jdbcTemplate.update(sql, RESOURCE_REINDEX_TIMESTAMP);
    result.setTotalRelationships(result.getTotalRelationships() + recordsUpserted);

    log.debug("migrateCallNumbers:: Call number upserted: {} records", recordsUpserted);
  }

  private void analyzeStagingTables() {
    var schema = getSchemaName(context);

    log.info("analyzeStagingTables:: Analyzing staging tables for better query performance...");
    jdbcTemplate.execute(String.format("ANALYZE %s.staging_instance", schema));
    jdbcTemplate.execute(String.format("ANALYZE %s.staging_holding", schema));
    jdbcTemplate.execute(String.format("ANALYZE %s.staging_item", schema));
    jdbcTemplate.execute(String.format("ANALYZE %s.staging_instance_subject", schema));
    jdbcTemplate.execute(String.format("ANALYZE %s.staging_instance_contributor", schema));
    jdbcTemplate.execute(String.format("ANALYZE %s.staging_instance_classification", schema));
    jdbcTemplate.execute(String.format("ANALYZE %s.staging_instance_call_number", schema));
    jdbcTemplate.execute(String.format("ANALYZE %s.staging_subject", schema));
    jdbcTemplate.execute(String.format("ANALYZE %s.staging_contributor", schema));
    jdbcTemplate.execute(String.format("ANALYZE %s.staging_classification", schema));
    jdbcTemplate.execute(String.format("ANALYZE %s.staging_call_number", schema));
    log.info("analyzeStagingTables:: Staging tables analyzed");
  }

  public void cleanupStagingTables() {
    var schema = getSchemaName(context);
    var sql = String.format("SELECT %s.cleanup_all_staging_tables()", schema);
    jdbcTemplate.execute(sql);
    log.info("cleanupStagingTables:: Staging tables truncated successfully");
  }

  /**
   * Sets the PostgreSQL work_mem parameter for the current transaction using SET LOCAL.
   * This optimizes query performance for memory-intensive operations during migration.
   *
   * @throws ReindexException if the work_mem value is invalid or the SET LOCAL command fails
   */
  private void setWorkMem() {
    var workMemValue = reindexConfigurationProperties.getMigrationWorkMem();

    // Validate the work_mem format for security
    if (!WORK_MEM_PATTERN.matcher(workMemValue).matches()) {
      throw new ReindexException("Invalid work_mem format: " + workMemValue
        + ". Must be a number followed by KB, MB, or GB (e.g., '64MB', '512KB', '1GB')");
    }

    log.info("setWorkMem:: Setting work_mem to {} for migration transaction", workMemValue);

    try {
      var sql = String.format("SET LOCAL work_mem = '%s'", workMemValue);
      jdbcTemplate.execute(sql);
      log.debug("setWorkMem:: Successfully set work_mem to {}", workMemValue);
    } catch (Exception e) {
      var errorMsg = "Failed to set work_mem to " + workMemValue + " for migration transaction";
      log.error("setWorkMem:: " + errorMsg, e);
      throw new ReindexException(errorMsg, e);
    }
  }
}
