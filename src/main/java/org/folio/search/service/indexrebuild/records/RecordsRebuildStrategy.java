package org.folio.search.service.indexrebuild.records;

import java.util.Map;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.event.ReindexRecordType;
import org.folio.search.model.types.InventoryRecordType;
import org.folio.search.service.indexrebuild.IndexRebuildTarget;

/**
 * What differs between indexes rebuilt from inventory records: one record of a type becomes one document. Everything
 * else (ranges, delivery, writing, delete tracking, finalization) is shared by the records pipeline.
 */
public interface RecordsRebuildStrategy {

  IndexRebuildTarget target();

  /**
   * Record type of the records inventory delivers for this target.
   */
  ReindexRecordType reindexRecordType();

  /**
   * Record type used to count records and to request them from inventory.
   */
  InventoryRecordType inventoryRecordType();

  /**
   * Checks that the index of the target is enabled.
   *
   * @throws RequestValidationException if it isn't
   */
  void validateEnabled();

  /**
   * Id of the document of a record. Document ids must match the ones written by events.
   */
  String documentId(String recordId, String tenantId);

  /**
   * Prepares a record of a tenant for document conversion, the same way events of the record are prepared. Must not
   * modify the given record.
   */
  Map<String, Object> prepare(String tenantId, Map<String, Object> recordData);
}
