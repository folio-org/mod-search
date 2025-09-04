package org.folio.search.service.reindex;

import static org.folio.search.utils.JdbcUtils.getSchemaName;

import java.util.HashMap;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import org.folio.search.exception.ReindexException;
import org.folio.search.model.reindex.DeduplicationResult;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
public class StagingDeduplicationService {

  private final JdbcTemplate jdbcTemplate;
  private final FolioExecutionContext context;
  private final ReindexCommonService reindexCommonService;

  public StagingDeduplicationService(JdbcTemplate jdbcTemplate,
                                     FolioExecutionContext context,
                                     ReindexCommonService reindexCommonService) {
    this.jdbcTemplate = jdbcTemplate;
    this.context = context;
    this.reindexCommonService = reindexCommonService;
  }

  @Transactional
  public DeduplicationResult deduplicateAllStagingTables() {
    return deduplicateAllStagingTables(null);
  }

  @Transactional
  public DeduplicationResult deduplicateAllStagingTables(String targetTenantId) {
    var result = new DeduplicationResult();
    var startTime = System.currentTimeMillis();

    try {
      if (targetTenantId != null) {
        log.info("Starting tenant-specific deduplication for tenant: {}", targetTenantId);
        // Delete existing data for this tenant from non-staging tables
        reindexCommonService.deleteRecordsByTenantId(targetTenantId);
      } else {
        log.info("Starting full deduplication of all staging tables");
      }

      // Analyze staging tables for better query performance
      analyzeStagingTables();

      // Phase 1: Instances
      log.info("Starting instances deduplication...");
      deduplicateInstances(result);
      log.info("Instances deduplication complete");

      // Phase 2: Holdings and instance relationships
      log.info("Starting holdings deduplication...");
      deduplicateHoldings(result);
      log.info("Holdings deduplication complete");

      log.info("Starting instance relationships deduplication...");
      deduplicateInstanceSubjects(result);
      log.info("Instance-subject deduplication complete");

      deduplicateInstanceContributors(result);
      log.info("Instance-contributor deduplication complete");

      deduplicateInstanceClassifications(result);
      log.info("Instance-classification deduplication complete");

      // Phase 3: Items
      deduplicateItems(result);
      log.info("Phase 3 complete: items deduplicated");

      // Phase 4: Instance call numbers
      deduplicateInstanceCallNumbers(result);
      log.info("Phase 4 complete: call numbers deduplicated");

      // Phase 5: Child resources (subjects, contributors, classifications, call numbers)
      log.info("Starting child resources deduplication...");
      deduplicateSubjects(result);
      log.info("Subject deduplication complete");

      deduplicateContributors(result);
      log.info("Contributor deduplication complete");

      deduplicateClassifications(result);
      log.info("Classification deduplication complete");

      deduplicateCallNumbers(result);
      log.info("Call number deduplication complete");

      // Cleanup staging tables
      cleanupStagingTables();

      var duration = System.currentTimeMillis() - startTime;
      result.setDuration(duration);

      log.info("Deduplication complete in {} ms: {}", duration, result);
      return result;
    } catch (Exception e) {
      log.error("Deduplication failed, staging tables preserved", e);
      throw new ReindexException("Failed to deduplicate staging tables", e);
    }
  }

  private void deduplicateInstances(DeduplicationResult result) {
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

  private void deduplicateHoldings(DeduplicationResult result) {
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

  private void deduplicateItems(DeduplicationResult result) {
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

  private void deduplicateInstanceSubjects(DeduplicationResult result) {
    var schema = getSchemaName(context);
    var sql = String.format("""
        INSERT INTO %s.instance_subject (instance_id, subject_id, tenant_id, shared)
        SELECT instance_id, subject_id, tenant_id, shared
        FROM %s.staging_instance_subject
        ORDER BY inserted_at DESC
        ON CONFLICT (subject_id, instance_id, tenant_id) DO UPDATE SET
            shared = EXCLUDED.shared
        """, schema, schema);

    var recordsUpserted = jdbcTemplate.update(sql);
    result.setTotalRelationships(result.getTotalRelationships() + recordsUpserted);

    log.debug("Instance-subject relationship upserted: {} records", recordsUpserted);
  }

  private void deduplicateInstanceContributors(DeduplicationResult result) {
    var schema = getSchemaName(context);
    var sql = String.format("""
        INSERT INTO %s.instance_contributor (instance_id, contributor_id, type_id, tenant_id, shared)
        SELECT instance_id, contributor_id, type_id, tenant_id, shared
        FROM %s.staging_instance_contributor
        ORDER BY inserted_at DESC
        ON CONFLICT (contributor_id, instance_id, type_id, tenant_id) DO UPDATE SET
            shared = EXCLUDED.shared
        """, schema, schema);

    var recordsUpserted = jdbcTemplate.update(sql);
    result.setTotalRelationships(result.getTotalRelationships() + recordsUpserted);

    log.debug("Instance-contributor relationship upserted: {} records", recordsUpserted);
  }

  private void deduplicateInstanceClassifications(DeduplicationResult result) {
    var schema = getSchemaName(context);
    var sql = String.format("""
        INSERT INTO %s.instance_classification (instance_id, classification_id, tenant_id, shared)
        SELECT instance_id, classification_id, tenant_id, shared
        FROM %s.staging_instance_classification
        ORDER BY inserted_at DESC
        ON CONFLICT (classification_id, instance_id, tenant_id) DO UPDATE SET
            shared = EXCLUDED.shared
        """, schema, schema);

    var recordsUpserted = jdbcTemplate.update(sql);
    result.setTotalRelationships(result.getTotalRelationships() + recordsUpserted);

    log.debug("Instance-classification relationship upserted: {} records", recordsUpserted);
  }

  private void deduplicateInstanceCallNumbers(DeduplicationResult result) {
    var schema = getSchemaName(context);
    var sql = String.format("""
        INSERT INTO %s.instance_call_number (call_number_id, item_id, instance_id, tenant_id, location_id)
        SELECT call_number_id, item_id, instance_id, tenant_id, location_id
        FROM %s.staging_instance_call_number
        ORDER BY inserted_at DESC
        ON CONFLICT (call_number_id, item_id, instance_id, tenant_id) DO UPDATE SET
            location_id = EXCLUDED.location_id
        """, schema, schema);

    var recordsUpserted = jdbcTemplate.update(sql);
    result.setTotalRelationships(result.getTotalRelationships() + recordsUpserted);

    log.debug("Instance-call number relationship upserted: {} records", recordsUpserted);
  }

  private void deduplicateSubjects(DeduplicationResult result) {
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

  private void deduplicateContributors(DeduplicationResult result) {
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

  private void deduplicateClassifications(DeduplicationResult result) {
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

  private void deduplicateCallNumbers(DeduplicationResult result) {
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

  private void cleanupStagingTables() {
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
}
