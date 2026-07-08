package org.folio.search.service;

import static java.util.Locale.ROOT;
import static org.folio.search.model.reconciliation.ReconciliationReport.Status.ERROR;
import static org.folio.search.model.reconciliation.ReconciliationReport.Status.MATCH;
import static org.folio.search.model.reconciliation.ReconciliationReport.Status.MISMATCH;
import static org.folio.spring.config.properties.FolioEnvironment.getFolioEnvName;
import static org.opensearch.client.RequestOptions.DEFAULT;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.model.reconciliation.ReconciliationReport;
import org.folio.search.model.reconciliation.ReconciliationReport.IndexComparison;
import org.folio.search.model.reconciliation.ReconciliationReport.Status;
import org.folio.search.model.reconciliation.ReconciliationReport.TableComparison;
import org.folio.search.model.types.ResourceType;
import org.folio.search.repository.IndexNameProvider;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.folio.spring.FolioModuleMetadata;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.core.CountRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ReconciliationService {

  private static final String COUNT_SQL = "SELECT COUNT(*) FROM %s.%s";

  private final RestHighLevelClient elasticsearchClient;
  private final JdbcTemplate jdbcTemplate;
  private final IndexNameProvider indexNameProvider;
  private final ResourceDescriptionService resourceDescriptionService;
  private final FolioModuleMetadata folioModuleMetadata;

  @Value("${folio.reconciliation.baseline-index-suffix:}")
  private String baselineIndexSuffix;

  @Value("${folio.reconciliation.baseline-schema-suffix:_mod_search}")
  private String baselineSchemaName;

  @Value("#{'${folio.reconciliation.tables:instance_subject,instance_contributor,instance_call_number}'.split(',')}")
  private List<String> tables;

  public ReconciliationReport reconcile(String tenantId) {
    log.info("reconcile:: Starting reconciliation [tenantId: {}]", tenantId);

    var osResults = reconcileOpenSearch(tenantId);
    var pgResults = reconcilePostgres(tenantId);
    var overallStatus = determineOverallStatus(osResults.values(), pgResults.values());

    log.info("reconcile:: Completed [tenantId: {}, status: {}]", tenantId, overallStatus);
    return new ReconciliationReport(tenantId, overallStatus, osResults, pgResults);
  }

  private Map<String, IndexComparison> reconcileOpenSearch(String tenantId) {
    return resourceDescriptionService.getResourceTypes().stream()
      .collect(Collectors.toMap(ResourceType::getName, rt -> compareIndex(rt, tenantId)));
  }

  private IndexComparison compareIndex(ResourceType resourceType, String tenantId) {
    var currentIndex = indexNameProvider.getIndexName(resourceType, tenantId);
    var baselineIndex = baselineIndexName(resourceType.getName(), tenantId);
    log.debug("compareIndex:: [resource: {}, baseline: {}, current: {}]",
      resourceType.getName(), baselineIndex, currentIndex);
    try {
      var baselineCount = countDocuments(baselineIndex);
      var currentCount = countDocuments(currentIndex);
      var status = baselineCount == currentCount ? MATCH : MISMATCH;
      return new IndexComparison(baselineIndex, currentIndex, baselineCount, currentCount, status, null);
    } catch (Exception e) {
      log.warn("compareIndex:: Failed to compare index [resource: {}, error: {}]", resourceType.getName(), e.getMessage());
      return new IndexComparison(baselineIndex, currentIndex, -1, -1, ERROR, e.getMessage());
    }
  }

  private Map<String, TableComparison> reconcilePostgres(String tenantId) {
    var currentSchema = folioModuleMetadata.getDBSchemaName(tenantId);
    var baselineSchema = tenantId.toLowerCase(ROOT) + baselineSchemaName;
    return tables.stream()
      .collect(Collectors.toMap(table -> table, table -> compareTable(table, baselineSchema, currentSchema)));
  }

  private TableComparison compareTable(String table, String baselineSchema, String currentSchema) {
    log.debug("compareTable:: [table: {}, baseline: {}, current: {}]", table, baselineSchema, currentSchema);
    try {
      var baselineCount = countRows(baselineSchema, table);
      var currentCount = countRows(currentSchema, table);
      var status = baselineCount == currentCount ? MATCH : MISMATCH;
      return new TableComparison(baselineSchema, currentSchema, baselineCount, currentCount, status, null);
    } catch (Exception e) {
      log.warn("compareTable:: Failed to compare table [{}.{}, error: {}]", currentSchema, table, e.getMessage());
      return new TableComparison(baselineSchema, currentSchema, -1, -1, ERROR, e.getMessage());
    }
  }

  private long countDocuments(String indexName) throws Exception {
    var response = elasticsearchClient.count(new CountRequest(indexName), DEFAULT);
    return response.getCount();
  }

  private long countRows(String schema, String table) {
    var sql = COUNT_SQL.formatted(schema, table);
    var count = jdbcTemplate.queryForObject(sql, Long.class);
    return count != null ? count : 0L;
  }

  private String baselineIndexName(String resource, String tenantId) {
    return getFolioEnvName().toLowerCase(ROOT) + "_" + resource.toLowerCase(ROOT) + "_" + tenantId + baselineIndexSuffix;
  }

  private Status determineOverallStatus(
    Iterable<IndexComparison> osResults,
    Iterable<TableComparison> pgResults
  ) {
    var status = MATCH;
    for (var r : osResults) {
      if (r.status() == ERROR) return ERROR;
      if (r.status() == MISMATCH) status = MISMATCH;
    }
    for (var r : pgResults) {
      if (r.status() == ERROR) return ERROR;
      if (r.status() == MISMATCH) status = MISMATCH;
    }
    return status;
  }
}
