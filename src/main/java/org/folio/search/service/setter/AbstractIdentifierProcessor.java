package org.folio.search.service.setter;

import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.folio.search.integration.InstanceReferenceDataService;

@RequiredArgsConstructor
public abstract class AbstractIdentifierProcessor<T> implements FieldProcessor<T, Set<String>> {

  private final InstanceReferenceDataService referenceDataService;
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
    return referenceDataService.fetchIdentifierIds(identifierNames);
  }
}
