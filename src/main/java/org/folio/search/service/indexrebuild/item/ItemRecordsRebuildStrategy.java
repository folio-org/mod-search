package org.folio.search.service.indexrebuild.item;

import static org.folio.search.utils.SearchUtils.SHARED_FIELD_NAME;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.event.ReindexRecordType;
import org.folio.search.model.types.InventoryRecordType;
import org.folio.search.service.indexrebuild.IndexRebuildTarget;
import org.folio.search.service.indexrebuild.records.RecordsRebuildStrategy;
import org.springframework.stereotype.Component;

/**
 * Item index rebuilt from item records: documents are scoped by tenant and marked shared when their instance is.
 */
@Component
@RequiredArgsConstructor
public class ItemRecordsRebuildStrategy implements RecordsRebuildStrategy {

  private final ItemSharedFlagResolver sharedFlagResolver;
  private final SearchConfigurationProperties searchConfiguration;

  @Override
  public IndexRebuildTarget target() {
    return IndexRebuildTarget.ITEM;
  }

  @Override
  public ReindexRecordType reindexRecordType() {
    return ReindexRecordType.ITEM;
  }

  @Override
  public InventoryRecordType inventoryRecordType() {
    return InventoryRecordType.ITEM;
  }

  @Override
  public void validateEnabled() {
    if (!Boolean.TRUE.equals(searchConfiguration.getIndexing().getItemIndexEnabled())) {
      throw new RequestValidationException("Item index is not enabled", "itemIndexEnabled", "false");
    }
  }

  @Override
  public String documentId(String recordId, String tenantId) {
    return ItemDocumentIds.of(recordId, tenantId);
  }

  @Override
  public Map<String, Object> prepare(String tenantId, Map<String, Object> recordData) {
    var item = new LinkedHashMap<>(recordData);
    if (sharedFlagResolver.isShared(tenantId, item)) {
      item.put(SHARED_FIELD_NAME, true);
    }
    return item;
  }
}
