package org.folio.search.service.reindex;

import static org.folio.search.utils.JdbcUtils.getSchemaName;

import java.util.HashMap;
import java.util.Map;
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
  public MigrationResult migrateAllStagingTables() {
    return migrateAllStagingTables(null);
  }

  @Transactional
  public MigrationResult migrateAllStagingTables(String targetTenantId) {
    var isMemberTenantRefresh = targetTenantId != null;
    var result = new MigrationResult();
    var startTime = System.currentTimeMillis();

    try {
      // Set work_mem for this transaction to optimize query performance
      setWorkMem();

      logMigrationStart(isMemberTenantRefresh, targetTenantId);
      
      // Analyze staging tables for better query performance
      analyzeStagingTables();

      // Handle member tenant specific operations before main migration
      if (isMemberTenantRefresh) {
        handleMemberTenantPreMigration(targetTenantId, result);
      }

      // Execute the main data migration phases
      executeMainMigrationPhases(result);

      var duration = System.currentTimeMillis() - startTime;
      result.setDuration(duration);

      log.info("Migration complete in {} ms: {}", duration, result);
      return result;
    } catch (Exception e) {
      log.error("Migration failed", e);
      throw new ReindexException("Failed to migrate staging tables", e);
    }
  }

  private void logMigrationStart(boolean isMemberTenantRefresh, String targetTenantId) {
    if (isMemberTenantRefresh) {
      log.info("Starting tenant-specific migration for tenant: {}", targetTenantId);
      // Note: For member tenant refresh, we need to identify stale relationships BEFORE deleting
      // existing data, so we can properly update child resource timestamps for OpenSearch sync
    } else {
      log.info("Starting full migration of all staging tables");
    }
  }

  private void handleMemberTenantPreMigration(String targetTenantId, MigrationResult result) {
    // For member tenant refresh: identify and cleanup stale relationships BEFORE clearing main tables
    // This ensures child resource timestamps are updated for proper OpenSearch synchronization
    log.info("Identifying stale relationships before clearing main tables for tenant: {}", targetTenantId);
    //todo: Could be removed since deletion by tenant id is executed, which still requires an update to cover all records
    cleanupStaleRelationships(targetTenantId, result);
    log.info("Stale relationship cleanup completed - child resource timestamps updated");

    // Now delete existing data for this tenant from non-staging tables
    log.info("Clearing existing tenant data from main tables for tenant: {}", targetTenantId);
    reindexCommonService.deleteRecordsByTenantId(targetTenantId);
    log.info("Main table cleanup completed for tenant: {}", targetTenantId);
  }

  private void executeMainMigrationPhases(MigrationResult result) {
    // Phase 1: Instances
    log.info("Starting instances migration...");
    migrateInstances(result);
    log.info("Instances migration complete");

    // Phase 2: Holdings and instance relationships
    log.info("Starting holdings migration...");
    migrateHoldings(result);
    log.info("Holdings migration complete");

    //todo: migrate items here, then resource tables like subjects, contributors, etc, then instance relationships
    log.info("Starting instance relationships migration...");
    migrateInstanceSubjects(result);
    log.info("Instance-subject migration complete");

    migrateInstanceContributors(result);
    log.info("Instance-contributor migration complete");

    migrateInstanceClassifications(result);
    log.info("Instance-classification migration complete");

    // Update last_updated_date for child resources that gained new relationships
    //todo: not needed
    updateChildResourceTimestamps(result);
    log.info("Child resource timestamps updated");

    // Phase 3: Items
    migrateItems(result);
    log.info("Phase 3 complete: items migrated");

    // Phase 4: Instance call numbers
    migrateInstanceCallNumbers(result);
    log.info("Phase 4 complete: call numbers migrated");

    // Phase 5: Child resources (subjects, contributors, classifications, call numbers)
    log.info("Starting child resources migration...");
    migrateSubjects(result);
    log.info("Subject migration complete");

    migrateContributors(result);
    log.info("Contributor migration complete");

    migrateClassifications(result);
    log.info("Classification migration complete");

    migrateCallNumbers(result);
    log.info("Call number migration complete");
  }

  private void migrateInstances(MigrationResult result) {
    var schema = getSchemaName(context);
    var sql = String.format("""
        INSERT INTO %s.instance (id, tenant_id, shared, is_bound_with, json)
        SELECT id, tenant_id, shared, is_bound_with, json
        FROM %s.staging_instance
        ORDER BY inserted_at DESC
        ON CONFLICT (id) DO UPDATE SET
            tenant_id = EXCLUDED.tenant_id,
            shared = EXCLUDED.shared,
            is_bound_with = EXCLUDED.is_bound_with,
            json = EXCLUDED.json
        """, schema, schema);

    var recordsUpserted = jdbcTemplate.update(sql);
    result.setTotalInstances(recordsUpserted);

    log.debug("Instance upserted: {} records", recordsUpserted);
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

    log.debug("Holding upserted: {} records", recordsUpserted);
  }

  private void migrateItems(MigrationResult result) {
    var schema = getSchemaName(context);
    var sql = String.format("""
        INSERT INTO %s.item (id, tenant_id, instance_id, holding_id, json)
        SELECT id, tenant_id, instance_id, holding_id, json
        FROM %s.staging_item
        ORDER BY inserted_at DESC
        ON CONFLICT (id, tenant_id) DO UPDATE SET
            instance_id = EXCLUDED.instance_id,
            holding_id = EXCLUDED.holding_id,
            json = EXCLUDED.json
        """, schema, schema);

    var recordsUpserted = jdbcTemplate.update(sql);
    result.setTotalItems(recordsUpserted);

    log.debug("Item upserted: {} records", recordsUpserted);
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

    log.debug("Instance-subject relationship upserted: {} records", recordsUpserted);
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

    log.debug("Instance-contributor relationship upserted: {} records", recordsUpserted);
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

    log.debug("Instance-classification relationship upserted: {} records", recordsUpserted);
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

    log.debug("Instance-call number relationship upserted: {} records", recordsUpserted);
  }

  private void migrateSubjects(MigrationResult result) {
    var schema = getSchemaName(context);
    var sql = String.format("""
        INSERT INTO %s.subject (id, value, authority_id, source_id, type_id, last_updated_date)
        SELECT id, value, authority_id, source_id, type_id, last_updated_date
        FROM %s.staging_subject
        ON CONFLICT (id) DO NOTHING
        """, schema, schema);

    var recordsUpserted = jdbcTemplate.update(sql);
    result.setTotalRelationships(result.getTotalRelationships() + recordsUpserted);

    log.debug("Subject upserted: {} records", recordsUpserted);
  }

  private void migrateContributors(MigrationResult result) {
    var schema = getSchemaName(context);
    var sql = String.format("""
        INSERT INTO %s.contributor (id, name, name_type_id, authority_id, last_updated_date)
        SELECT id, name, name_type_id, authority_id, last_updated_date
        FROM %s.staging_contributor
        ON CONFLICT (id) DO NOTHING
        """, schema, schema);

    var recordsUpserted = jdbcTemplate.update(sql);
    result.setTotalRelationships(result.getTotalRelationships() + recordsUpserted);

    log.debug("Contributor upserted: {} records", recordsUpserted);
  }

  private void migrateClassifications(MigrationResult result) {
    var schema = getSchemaName(context);
    var sql = String.format("""
        INSERT INTO %s.classification (id, number, type_id, last_updated_date)
        SELECT id, number, type_id, last_updated_date
        FROM %s.staging_classification
        ON CONFLICT (id) DO NOTHING
        """, schema, schema);

    var recordsUpserted = jdbcTemplate.update(sql);
    result.setTotalRelationships(result.getTotalRelationships() + recordsUpserted);

    log.debug("Classification upserted: {} records", recordsUpserted);
  }

  private void migrateCallNumbers(MigrationResult result) {
    var schema = getSchemaName(context);
    var sql = String.format("""
        INSERT INTO %s.call_number
        (id, call_number, call_number_prefix, call_number_suffix, call_number_type_id, last_updated_date)
        SELECT id, call_number, call_number_prefix, call_number_suffix, call_number_type_id, last_updated_date
        FROM %s.staging_call_number
        ON CONFLICT (id) DO NOTHING
        """, schema, schema);

    var recordsUpserted = jdbcTemplate.update(sql);
    result.setTotalRelationships(result.getTotalRelationships() + recordsUpserted);

    log.debug("Call number upserted: {} records", recordsUpserted);
  }

  private void analyzeStagingTables() {
    var schema = getSchemaName(context);

    log.info("Analyzing staging tables for better query performance...");
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
    log.info("Staging tables analyzed");
  }

  /**
   * Updates last_updated_date for child resources that have new relationships from staging tables.
   * Uses efficient JOIN-based updates to identify affected child resources with optimal performance.
   * This ensures child resources with new member tenant relationships are included in timestamp-based upload.
   */
  private void updateChildResourceTimestamps(MigrationResult result) {
    var schema = getSchemaName(context);
    var startTime = System.currentTimeMillis();

    // Update subjects that gained new relationships using JOIN approach
    var subjectUpdateSql = String.format("""
        UPDATE %s.subject s
        SET last_updated_date = CURRENT_TIMESTAMP
        FROM %s.staging_instance_subject sis
        WHERE s.id = sis.subject_id
        """, schema, schema);

    var subjectsUpdated = jdbcTemplate.update(subjectUpdateSql);
    log.debug("Updated {} subjects with new relationships", subjectsUpdated);

    // Update contributors that gained new relationships using JOIN approach
    var contributorUpdateSql = String.format("""
        UPDATE %s.contributor c
        SET last_updated_date = CURRENT_TIMESTAMP
        FROM %s.staging_instance_contributor sic
        WHERE c.id = sic.contributor_id
        """, schema, schema);

    var contributorsUpdated = jdbcTemplate.update(contributorUpdateSql);
    log.debug("Updated {} contributors with new relationships", contributorsUpdated);

    // Update classifications that gained new relationships using JOIN approach
    var classificationUpdateSql = String.format("""
        UPDATE %s.classification cl
        SET last_updated_date = CURRENT_TIMESTAMP
        FROM %s.staging_instance_classification sicl
        WHERE cl.id = sicl.classification_id
        """, schema, schema);

    var classificationsUpdated = jdbcTemplate.update(classificationUpdateSql);
    log.debug("Updated {} classifications with new relationships", classificationsUpdated);

    // Update call numbers that gained new relationships using JOIN approach
    var callNumberUpdateSql = String.format("""
        UPDATE %s.call_number cn
        SET last_updated_date = CURRENT_TIMESTAMP
        FROM %s.staging_instance_call_number sicn
        WHERE cn.id = sicn.call_number_id
        """, schema, schema);

    var callNumbersUpdated = jdbcTemplate.update(callNumberUpdateSql);
    log.debug("Updated {} call numbers with new relationships", callNumbersUpdated);

    var duration = System.currentTimeMillis() - startTime;
    var totalUpdated = subjectsUpdated + contributorsUpdated + classificationsUpdated + callNumbersUpdated;

    log.info("Child resource timestamp updates completed "
        + "in {} ms: {} subjects, {} contributors, {} classifications, {} call numbers (total: {})",
        duration, subjectsUpdated, contributorsUpdated, classificationsUpdated, callNumbersUpdated, totalUpdated);

    // Track in migration result for monitoring
    result.setChildResourceTimestampUpdates(totalUpdated);
  }

  /**
   * Cleans up stale relationships for member tenant refreshes by identifying records
   * in main tables that don't exist in staging tables, then updating child resource
   * timestamps and deleting stale relationship records.
   * 
   * IMPORTANT: This method must be called BEFORE deleteRecordsByTenantId() so that
   * the comparison between main tables (containing existing data) and staging tables
   * (containing fresh data) can properly identify stale relationships and update
   * child resource timestamps for OpenSearch synchronization.
   */
  private void cleanupStaleRelationships(String targetTenantId, MigrationResult result) {
    var startTime = System.currentTimeMillis();
    log.info("Starting stale relationship cleanup for tenant: {}", targetTenantId);

    try {
      // Clean up each relationship type
      cleanupStaleSubjectRelationships(targetTenantId, result);
      cleanupStaleContributorRelationships(targetTenantId, result);
      cleanupStaleClassificationRelationships(targetTenantId, result);
      cleanupStaleCallNumberRelationships(targetTenantId, result);

      var duration = System.currentTimeMillis() - startTime;
      log.info("Stale relationship cleanup completed for "
          + "tenant {} in {} ms: {} subjects, {} contributors, {} classifications, {} call numbers",
          targetTenantId, duration,
          result.getStaleSubjectCleanups(), result.getStaleContributorCleanups(),
          result.getStaleClassificationCleanups(), result.getStaleCallNumberCleanups());

    } catch (Exception e) {
      log.error("Stale relationship cleanup failed for tenant: {}", targetTenantId, e);
      throw new ReindexException("Failed to cleanup stale relationships for tenant: " + targetTenantId, e);
    }
  }

  private void cleanupStaleSubjectRelationships(String targetTenantId, MigrationResult result) {
    var schema = getSchemaName(context);

    // Update timestamps for subjects that have stale relationships
    var updateTimestampsSql = String.format("""
        UPDATE %s.subject s
        SET last_updated_date = CURRENT_TIMESTAMP
        FROM (
            SELECT main.subject_id
            FROM %s.instance_subject AS main
            LEFT JOIN %s.staging_instance_subject AS staging
              ON main.instance_id = staging.instance_id
              AND main.subject_id = staging.subject_id
              AND main.tenant_id = staging.tenant_id
            WHERE main.tenant_id = ?
              AND staging.instance_id IS NULL
        ) AS stale
        WHERE s.id = stale.subject_id
        """, schema, schema, schema);

    var timestampsUpdated = jdbcTemplate.update(updateTimestampsSql, targetTenantId);

    // Delete stale subject relationships
    var deleteStaleRelationshipsSql = String.format("""
        DELETE FROM %s.instance_subject main
        USING (
          SELECT main.instance_id, main.subject_id
          FROM %s.instance_subject AS main
          LEFT JOIN %s.staging_instance_subject AS staging
            ON main.instance_id = staging.instance_id
            AND main.subject_id = staging.subject_id
            AND main.tenant_id = staging.tenant_id
          WHERE main.tenant_id = ?
            AND staging.instance_id IS NULL
        ) AS stale
        WHERE main.tenant_id = ?
          AND main.instance_id = stale.instance_id
          AND main.subject_id = stale.subject_id
        """, schema, schema, schema);

    var relationshipsDeleted = jdbcTemplate.update(deleteStaleRelationshipsSql, targetTenantId, targetTenantId);

    result.setStaleSubjectCleanups(relationshipsDeleted);
    log.debug("Cleaned up {} stale subject relationships, updated {} subject timestamps",
        relationshipsDeleted, timestampsUpdated);
  }

  private void cleanupStaleContributorRelationships(String targetTenantId, MigrationResult result) {
    var schema = getSchemaName(context);

    // Update timestamps for contributors that have stale relationships
    var updateTimestampsSql = String.format("""
        UPDATE %s.contributor c
        SET last_updated_date = CURRENT_TIMESTAMP
        FROM (
            SELECT main.contributor_id
            FROM %s.instance_contributor AS main
            LEFT JOIN %s.staging_instance_contributor AS staging
              ON main.instance_id = staging.instance_id
              AND main.contributor_id = staging.contributor_id
              AND main.type_id = staging.type_id
              AND main.tenant_id = staging.tenant_id
            WHERE main.tenant_id = ?
              AND staging.instance_id IS NULL
        ) AS stale
        WHERE c.id = stale.contributor_id
        """, schema, schema, schema);

    var timestampsUpdated = jdbcTemplate.update(updateTimestampsSql, targetTenantId);

    // Delete stale contributor relationships
    var deleteStaleRelationshipsSql = String.format("""
        DELETE FROM %s.instance_contributor main
        USING (
          SELECT main.instance_id, main.contributor_id, main.type_id
          FROM %s.instance_contributor AS main
          LEFT JOIN %s.staging_instance_contributor AS staging
            ON main.instance_id = staging.instance_id
            AND main.contributor_id = staging.contributor_id
            AND main.type_id = staging.type_id
            AND main.tenant_id = staging.tenant_id
          WHERE main.tenant_id = ?
            AND staging.instance_id IS NULL
        ) AS stale
        WHERE main.tenant_id = ?
          AND main.instance_id = stale.instance_id
          AND main.contributor_id = stale.contributor_id
          AND main.type_id = stale.type_id
        """, schema, schema, schema);

    var relationshipsDeleted = jdbcTemplate.update(deleteStaleRelationshipsSql, targetTenantId, targetTenantId);

    result.setStaleContributorCleanups(relationshipsDeleted);
    log.debug("Cleaned up {} stale contributor relationships, updated {} contributor timestamps",
        relationshipsDeleted, timestampsUpdated);
  }

  private void cleanupStaleClassificationRelationships(String targetTenantId, MigrationResult result) {
    var schema = getSchemaName(context);

    // Update timestamps for classifications that have stale relationships
    var updateTimestampsSql = String.format("""
        UPDATE %s.classification cl
        SET last_updated_date = CURRENT_TIMESTAMP
        FROM (
            SELECT main.classification_id
            FROM %s.instance_classification AS main
            LEFT JOIN %s.staging_instance_classification AS staging
              ON main.instance_id = staging.instance_id
              AND main.classification_id = staging.classification_id
              AND main.tenant_id = staging.tenant_id
            WHERE main.tenant_id = ?
              AND staging.instance_id IS NULL
        ) AS stale
        WHERE cl.id = stale.classification_id
        """, schema, schema, schema);

    var timestampsUpdated = jdbcTemplate.update(updateTimestampsSql, targetTenantId);

    // Delete stale classification relationships
    var deleteStaleRelationshipsSql = String.format("""
        DELETE FROM %s.instance_classification main
        USING (
          SELECT main.instance_id, main.classification_id
          FROM %s.instance_classification AS main
          LEFT JOIN %s.staging_instance_classification AS staging
            ON main.instance_id = staging.instance_id
            AND main.classification_id = staging.classification_id
            AND main.tenant_id = staging.tenant_id
          WHERE main.tenant_id = ?
            AND staging.instance_id IS NULL
        ) AS stale
        WHERE main.tenant_id = ?
          AND main.instance_id = stale.instance_id
          AND main.classification_id = stale.classification_id
        """, schema, schema, schema);

    var relationshipsDeleted = jdbcTemplate.update(deleteStaleRelationshipsSql, targetTenantId, targetTenantId);

    result.setStaleClassificationCleanups(relationshipsDeleted);
    log.debug("Cleaned up {} stale classification relationships, updated {} classification timestamps",
        relationshipsDeleted, timestampsUpdated);
  }

  private void cleanupStaleCallNumberRelationships(String targetTenantId, MigrationResult result) {
    var schema = getSchemaName(context);

    // Update timestamps for call numbers that have stale relationships
    var updateTimestampsSql = String.format("""
        UPDATE %s.call_number cn
        SET last_updated_date = CURRENT_TIMESTAMP
        FROM (
            SELECT main.call_number_id
            FROM %s.instance_call_number AS main
            LEFT JOIN %s.staging_instance_call_number AS staging
              ON main.call_number_id = staging.call_number_id
              AND main.item_id = staging.item_id
              AND main.instance_id = staging.instance_id
              AND main.tenant_id = staging.tenant_id
            WHERE main.tenant_id = ?
              AND staging.instance_id IS NULL
        ) AS stale
        WHERE cn.id = stale.call_number_id
        """, schema, schema, schema);

    var timestampsUpdated = jdbcTemplate.update(updateTimestampsSql, targetTenantId);

    // Delete stale call number relationships
    var deleteStaleRelationshipsSql = String.format("""
        DELETE FROM %s.instance_call_number main
        USING (
          SELECT main.call_number_id, main.item_id, main.instance_id
          FROM %s.instance_call_number AS main
          LEFT JOIN %s.staging_instance_call_number AS staging
            ON main.call_number_id = staging.call_number_id
            AND main.item_id = staging.item_id
            AND main.instance_id = staging.instance_id
            AND main.tenant_id = staging.tenant_id
          WHERE main.tenant_id = ?
            AND staging.instance_id IS NULL
        ) AS stale
        WHERE main.tenant_id = ?
          AND main.call_number_id = stale.call_number_id
          AND main.item_id = stale.item_id
          AND main.instance_id = stale.instance_id
        """, schema, schema, schema);

    var relationshipsDeleted = jdbcTemplate.update(deleteStaleRelationshipsSql, targetTenantId, targetTenantId);

    result.setStaleCallNumberCleanups(relationshipsDeleted);
    log.debug("Cleaned up {} stale call number relationships, updated {} call number timestamps",
        relationshipsDeleted, timestampsUpdated);
  }

  public void cleanupStagingTables() {
    var schema = getSchemaName(context);
    var sql = String.format("SELECT %s.cleanup_all_staging_tables()", schema);
    jdbcTemplate.execute(sql);
    log.info("Staging tables truncated successfully");
  }

  public Map<String, Long> getStagingTableStats() {
    var schema = getSchemaName(context);
    var sql = String.format("SELECT * FROM %s.get_staging_table_stats()", schema);

    var results = jdbcTemplate.queryForList(sql);
    var stats = new HashMap<String, Long>();

    for (var row : results) {
      stats.put((String) row.get("table_name"), ((Number) row.get("record_count")).longValue());
    }
    return stats;
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

    log.info("Setting work_mem to {} for migration transaction", workMemValue);

    try {
      var sql = String.format("SET LOCAL work_mem = '%s'", workMemValue);
      jdbcTemplate.execute(sql);
      log.debug("Successfully set work_mem to {}", workMemValue);
    } catch (Exception e) {
      var errorMsg = "Failed to set work_mem to " + workMemValue + " for migration transaction";
      log.error(errorMsg, e);
      throw new ReindexException(errorMsg, e);
    }
  }
}
