package org.folio.search.service.indexrebuild.derived;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.ListUtils;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.exception.ReindexException;
import org.folio.search.repository.PrimaryResourceRepository;
import org.folio.search.service.converter.MultiTenantSearchDocumentConverter;
import org.springframework.stereotype.Component;

/**
 * Rebuilds documents of a derived index from its source index. A key no source document has anymore has its document
 * removed.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class DerivedDocumentWriter {

  private static final int KEYS_BATCH_SIZE = 500;

  private final DerivedIndexReader reader;
  private final MultiTenantSearchDocumentConverter documentConverter;
  private final PrimaryResourceRepository resourceRepository;

  /**
   * Rebuilds documents of the given keys.
   *
   * @throws ReindexException if documents couldn't be written
   */
  public void rebuild(DerivedRebuildStrategy strategy, String tenantId, Collection<String> keys) {
    ListUtils.partition(List.copyOf(keys), KEYS_BATCH_SIZE).forEach(batch -> rebuildBatch(strategy, tenantId, batch));
  }

  private void rebuildBatch(DerivedRebuildStrategy strategy, String tenantId, List<String> keys) {
    var requested = new HashSet<>(keys);
    var sourceByKey = new HashMap<String, List<Map<String, Object>>>();
    for (var source : reader.findSourceDocuments(strategy, tenantId, keys)) {
      DerivedIndexReader.keysOf(strategy, source).stream()
        .filter(requested::contains)
        .forEach(key -> sourceByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(source));
    }
    var events = new ArrayList<ResourceEvent>();
    for (var key : keys) {
      var document = Optional.ofNullable(sourceByKey.get(key)).flatMap(strategy::build);
      events.add(toEvent(strategy, key, tenantId, document.orElse(null)));
    }
    var response = resourceRepository.indexResources(documentConverter.convertForReindex(events));
    if (response.getStatus() == FolioIndexOperationResponse.StatusEnum.ERROR) {
      throw new ReindexException(response.getErrorMessage());
    }
    var deleted = events.stream().filter(event -> event.getType() == ResourceEventType.DELETE).count();
    log.debug("rebuildBatch:: documents rebuilt [resource: {}, keys: {}, indexed: {}, deleted: {}]",
      strategy.target().getResource(), keys.size(), keys.size() - deleted, deleted);
  }

  private static ResourceEvent toEvent(DerivedRebuildStrategy strategy, String key, String tenantId,
                                       Map<String, Object> document) {
    return new ResourceEvent()
      .id(key)
      .type(Objects.isNull(document) ? ResourceEventType.DELETE : ResourceEventType.REINDEX)
      .tenant(tenantId)
      .resourceName(strategy.target().getResourceType().getName())
      ._new(document);
  }
}
