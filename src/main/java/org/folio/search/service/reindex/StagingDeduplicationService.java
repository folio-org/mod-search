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
    var sql = String.format("SELECT * FROM %s.dedup_staging_instance()", schema);

    var dedupResult = jdbcTemplate.queryForMap(sql);
    var recordsUpserted = ((Number) dedupResult.get("records_upserted")).longValue();
    result.setTotalInstances(recordsUpserted);

    log.debug("Instance deduplication: {}", dedupResult);
  }

  private void deduplicateHoldings(DeduplicationResult result) {
    var schema = getSchemaName(context);
    var sql = String.format("SELECT * FROM %s.dedup_staging_holding()", schema);

    var dedupResult = jdbcTemplate.queryForMap(sql);
    var recordsUpserted = ((Number) dedupResult.get("records_upserted")).longValue();
    result.setTotalHoldings(recordsUpserted);

    log.debug("Holding deduplication: {}", dedupResult);
  }

  private void deduplicateItems(DeduplicationResult result) {
    var schema = getSchemaName(context);
    var sql = String.format("SELECT * FROM %s.dedup_staging_item()", schema);

    var dedupResult = jdbcTemplate.queryForMap(sql);
    var recordsUpserted = ((Number) dedupResult.get("records_upserted")).longValue();
    result.setTotalItems(recordsUpserted);

    log.debug("Item deduplication: {}", dedupResult);
  }

  private void deduplicateInstanceSubjects(DeduplicationResult result) {
    var schema = getSchemaName(context);
    var sql = String.format("SELECT * FROM %s.dedup_staging_instance_subject()", schema);

    var dedupResult = jdbcTemplate.queryForMap(sql);
    var recordsUpserted = ((Number) dedupResult.get("records_upserted")).longValue();
    result.setTotalRelationships(result.getTotalRelationships() + recordsUpserted);

    log.debug("Instance-subject relationship deduplication: {}", dedupResult);
  }

  private void deduplicateInstanceContributors(DeduplicationResult result) {
    var schema = getSchemaName(context);
    var sql = String.format("SELECT * FROM %s.dedup_staging_instance_contributor()", schema);

    var dedupResult = jdbcTemplate.queryForMap(sql);
    var recordsUpserted = ((Number) dedupResult.get("records_upserted")).longValue();
    result.setTotalRelationships(result.getTotalRelationships() + recordsUpserted);

    log.debug("Instance-contributor relationship deduplication: {}", dedupResult);
  }

  private void deduplicateInstanceClassifications(DeduplicationResult result) {
    var schema = getSchemaName(context);
    var sql = String.format("SELECT * FROM %s.dedup_staging_instance_classification()", schema);

    var dedupResult = jdbcTemplate.queryForMap(sql);
    var recordsUpserted = ((Number) dedupResult.get("records_upserted")).longValue();
    result.setTotalRelationships(result.getTotalRelationships() + recordsUpserted);

    log.debug("Instance-classification relationship deduplication: {}", dedupResult);
  }

  private void deduplicateInstanceCallNumbers(DeduplicationResult result) {
    var schema = getSchemaName(context);
    var sql = String.format("SELECT * FROM %s.dedup_staging_instance_call_number()", schema);

    var dedupResult = jdbcTemplate.queryForMap(sql);
    var recordsUpserted = ((Number) dedupResult.get("records_upserted")).longValue();
    result.setTotalRelationships(result.getTotalRelationships() + recordsUpserted);

    log.debug("Instance-call number relationship deduplication: {}", dedupResult);
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
