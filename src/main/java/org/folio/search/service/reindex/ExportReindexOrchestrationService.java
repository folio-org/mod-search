package org.folio.search.service.reindex;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.message.FormattedMessage;
import org.folio.s3.client.FolioS3Client;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.exception.ReindexException;
import org.folio.search.model.event.ReindexFileReadyEvent;
import org.folio.search.model.event.ReindexRecordsEvent;
import org.folio.search.repository.PrimaryResourceRepository;
import org.folio.search.service.converter.MultiTenantSearchDocumentConverter;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.FolioExecutionContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@ConditionalOnProperty(name = "folio.reindex.reindex-type", havingValue = "EXPORT", matchIfMissing = true)
public class ExportReindexOrchestrationService extends ReindexOrchestrationService {

  private final ReindexConfigurationProperties reindexConfigurationProperties;
  private final JsonConverter jsonConverter;
  private final FolioS3Client folioS3Client;

  public ExportReindexOrchestrationService(ReindexUploadRangeIndexService uploadRangeService,
                                           ReindexConfigurationProperties reindexConfigurationProperties,
                                           ReindexMergeRangeIndexService mergeRangeService,
                                           ReindexStatusService reindexStatusService,
                                           PrimaryResourceRepository elasticRepository,
                                           ReindexService reindexService,
                                           MultiTenantSearchDocumentConverter documentConverter,
                                           FolioExecutionContext context,
                                           JsonConverter jsonConverter,
                                           FolioS3Client folioS3Client) {
    super(uploadRangeService, mergeRangeService, reindexStatusService, elasticRepository,
      reindexService, documentConverter, context);
    this.reindexConfigurationProperties = reindexConfigurationProperties;
    this.jsonConverter = jsonConverter;
    this.folioS3Client = folioS3Client;
  }

  @Override
  public boolean process(ReindexFileReadyEvent event) {
    var memberTenantId = getMemberTenantIdForProcessing();

    log.info("process:: ReindexFileReadyEvent [traceId: {}, rangeId: {}, tenantId: {}, memberTenantId: {}, "
             + "recordType: {}]",
      event.getTraceId(), event.getRangeId(), event.getTenantId(), memberTenantId, event.getRecordType());
    var entityType = event.getRecordType().getEntityType();

    try {
      readAndSave(event);
      handleMergeSuccess(entityType, event.getRangeId());
    } catch (PessimisticLockingFailureException ex) {
      log.warn(new FormattedMessage("process:: ReindexFileReadyEvent indexing recoverable error"
                                    + " [rangeId: {}, error: {}]", event.getRangeId(), ex.getMessage()), ex);
      throw new ReindexException(ex.getMessage());
    } catch (Exception ex) {
      handleMergeFailure(entityType, event.getRangeId(), ex);
      return true;
    } finally {
      if (memberTenantId != null) {
        ReindexContext.clearMemberTenantId();
      }
    }

    startUploadOnMergeCompletion();
    return true;
  }

  private void readAndSave(ReindexFileReadyEvent event) throws IOException {
    var batchSize = reindexConfigurationProperties.getMergeExportBatchSize();
    try (var is = folioS3Client.read(event.getObjectKey());
         var isr = new InputStreamReader(is, StandardCharsets.UTF_8);
         var reader = new BufferedReader(isr)) {
      List<Object> batch = new ArrayList<>(batchSize);
      String line;
      while ((line = reader.readLine()) != null) {
        if (!line.isBlank()) {
          batch.add(jsonConverter.fromJsonToMap(line));
        }
        if (batch.size() >= batchSize) {
          saveBatch(event, batch, true);
        }
      }
      saveBatch(event, batch, false);
    }
  }

  private void saveBatch(ReindexFileReadyEvent event, List<Object> batch, boolean clearAfterSave) {
    if (batch.isEmpty()) {
      return;
    }
    mergeRangeService.saveEntities(toReindexRecordsEvent(event, batch));
    if (clearAfterSave) {
      batch.clear();
    }
  }

  private ReindexRecordsEvent toReindexRecordsEvent(ReindexFileReadyEvent event, List<Object> records) {
    var reindexRecordsEvent = new ReindexRecordsEvent();
    reindexRecordsEvent.setRangeId(event.getRangeId());
    reindexRecordsEvent.setTenant(event.getTenantId());
    reindexRecordsEvent.setRecordType(event.getRecordType());
    reindexRecordsEvent.setRecords(records);
    return reindexRecordsEvent;
  }
}
