package org.folio.search.service.browse;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.service.reindex.jdbc.V2BrowseDirtyIdRepository;
import org.folio.search.utils.V2BrowseIdExtractor;
import org.folio.search.utils.V2BrowseIdExtractor.TouchedBrowseIds;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class V2BrowseDirtyIdEnqueueHelper {

  private static final Map<ReindexEntityType, Function<TouchedBrowseIds, Set<String>>>
    BROWSE_TYPE_EXTRACTORS = Map.of(
    ReindexEntityType.CONTRIBUTOR, TouchedBrowseIds::contributorIds,
    ReindexEntityType.SUBJECT, TouchedBrowseIds::subjectIds,
    ReindexEntityType.CLASSIFICATION, TouchedBrowseIds::classificationIds,
    ReindexEntityType.CALL_NUMBER, TouchedBrowseIds::callNumberIds
  );

  private final V2BrowseDirtyIdRepository dirtyIdRepository;

  public void enqueueTouched(String ownerTenantId, V2BrowseIdExtractor.TouchedBrowseIds touched) {
    var allRows = new ArrayList<V2BrowseDirtyIdRepository.BrowseTypeAndId>();
    for (var entry : BROWSE_TYPE_EXTRACTORS.entrySet()) {
      var ids = entry.getValue().apply(touched);
      if (!ids.isEmpty()) {
        var browseType = entry.getKey().getType();
        for (var id : ids) {
          allRows.add(new V2BrowseDirtyIdRepository.BrowseTypeAndId(browseType, id));
        }
      }
    }
    if (!allRows.isEmpty()) {
      dirtyIdRepository.enqueueBatch(ownerTenantId, allRows);
    }
  }
}
