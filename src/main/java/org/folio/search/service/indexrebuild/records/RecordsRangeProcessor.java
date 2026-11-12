package org.folio.search.service.indexrebuild.records;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.message.FormattedMessage;
import org.folio.search.model.event.ReindexFileReadyEvent;
import org.folio.search.model.event.ReindexRecordType;
import org.folio.search.model.event.ReindexRecordsEvent;
import org.folio.search.service.indexrebuild.IndexRebuildRangeIds;
import org.folio.search.service.indexrebuild.jdbc.IndexRebuildRangeRepository;
import org.folio.search.service.indexrebuild.jdbc.IndexRebuildStateRepository;
import org.folio.search.service.indexrebuild.model.IndexRebuildRange;
import org.folio.search.service.indexrebuild.model.IndexRebuildRangeStatus;
import org.springframework.core.retry.RetryException;
import org.springframework.stereotype.Component;

/**
 * Processes records delivered by inventory for a range of a rebuild. Records of an unknown range belong to an abandoned
 * run, and records of an already finished range were redelivered, so both are ignored.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class RecordsRangeProcessor {

  private final RecordsRebuildStrategies strategies;
  private final IndexRebuildRangeRepository rangeRepository;
  private final IndexRebuildStateRepository stateRepository;
  private final RecordsIndexWriter indexWriter;
  private final RecordsFileReader fileReader;
  private final RecordsFinalizer finalizer;

  public boolean handles(ReindexRecordType recordType, String rangeId) {
    return IndexRebuildRangeIds.isRebuildRange(rangeId) && strategies.find(recordType).isPresent();
  }

  @SuppressWarnings("unchecked")
  public void process(ReindexRecordsEvent event) {
    var strategy = strategy(event.getRecordType());
    var records = event.getRecords().stream().map(source -> (Map<String, Object>) source).toList();
    log.debug("process:: received records [resource: {}, rangeId: {}, records: {}]", strategy.target().getResource(),
      event.getRangeId(), records.size());
    process(strategy, event.getRangeId(), range -> indexWriter.write(strategy, range.tenantId(), records));
  }

  public void process(ReindexFileReadyEvent event) {
    var strategy = strategy(event.getRecordType());
    log.debug("process:: received file [resource: {}, rangeId: {}, objectKey: {}]", strategy.target().getResource(),
      event.getRangeId(), event.getObjectKey());
    process(strategy, event.getRangeId(), range -> fileReader.read(event.getObjectKey(),
      batch -> indexWriter.write(strategy, range.tenantId(), batch)));
  }

  private void process(RecordsRebuildStrategy strategy, String rangeId, RangeWriter writer) {
    var target = strategy.target();
    var range = rangeRepository.findById(target, UUID.fromString(rangeId)).orElse(null);
    if (range == null || range.status() == IndexRebuildRangeStatus.SUCCESS) {
      log.info("process:: ignoring records of unknown or finished range [resource: {}, rangeId: {}]",
        target.getResource(), rangeId);
      return;
    }
    try {
      writer.write(range);
      rangeRepository.markSuccess(range.id());
      log.debug("process:: range processed [resource: {}, tenant: {}, rangeId: {}]", target.getResource(),
        range.tenantId(), rangeId);
      finalizer.tryFinalize(strategy, range.tenantId());
    } catch (IOException | RetryException | RuntimeException e) {
      log.warn(new FormattedMessage("process:: failed to process range [resource: {}, rangeId: {}]",
        target.getResource(), rangeId), e);
      rangeRepository.markFailed(range.id(), e.getMessage());
      stateRepository.markFailed(target, range.tenantId(), e.getMessage());
    }
  }

  private RecordsRebuildStrategy strategy(ReindexRecordType recordType) {
    return strategies.find(recordType)
      .orElseThrow(() -> new IllegalArgumentException("Not rebuilt from records: " + recordType));
  }

  @FunctionalInterface
  private interface RangeWriter {

    void write(IndexRebuildRange range) throws IOException, RetryException;
  }
}
