package org.folio.search.service.setter;

import static org.folio.search.client.InventoryReferenceDataClient.ReferenceDataType.IDENTIFIER_TYPES;

import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.integration.ReferenceDataService;

@Log4j2
@RequiredArgsConstructor
public abstract class AbstractIdentifierProcessor<T> implements FieldProcessor<T, Set<String>> {

  private final ReferenceDataService referenceDataService;
  private final List<String> identifierNames;

  public List<String> getIdentifierNames() {
    return identifierNames;
  }

  /**
   * Returns set of identifier ids from cache.
   *
   * @return {@link List} of {@link String} identifier ids that matches names.
   */
  protected Set<String> fetchIdentifierIdsFromCache() {
    var identifierTypeIds = referenceDataService.fetchReferenceData(IDENTIFIER_TYPES, getIdentifierNames());
    if (identifierTypeIds.isEmpty()) {
      log.warn("Failed to provide identifiers for processor: '{}']",
        this.getClass().getSimpleName());
    }
    return identifierTypeIds;
  }
}
