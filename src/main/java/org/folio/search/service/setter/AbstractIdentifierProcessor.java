package org.folio.search.service.setter;

import static java.util.stream.Collectors.toCollection;
import static org.folio.search.client.InventoryReferenceDataClient.ReferenceDataType.IDENTIFIER_TYPES;
import static org.folio.search.utils.CollectionUtils.toStreamSafe;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.Identifier;
import org.folio.search.integration.folio.ReferenceDataService;
import org.folio.search.model.client.CqlQueryParam;

@Log4j2
@RequiredArgsConstructor
public abstract class AbstractIdentifierProcessor<T> implements FieldProcessor<T, Set<String>> {

  private final ReferenceDataService referenceDataService;

  public abstract List<String> getIdentifierNames();

  protected Set<String> getIdentifierValues(T entity) {
    return filterIdentifiersValue(getIdentifiers(entity));
  }

  protected Stream<String> getIdentifierValuesStream(T entity) {
    return getIdentifierValues(entity).stream();
  }

  protected abstract List<Identifier> getIdentifiers(T entity);

  /**
   * Returns set of filtered identifiers value from event body by specified set of types.
   *
   * @param identifiers event body as map to process
   * @return {@link Set} of filtered identifiers value
   */
  protected Set<String> filterIdentifiersValue(List<Identifier> identifiers) {
    var identifierTypeIds = fetchIdentifierIdsFromCache();

    return toStreamSafe(identifiers)
      .filter(identifier -> identifierTypeIds.contains(identifier.getIdentifierTypeId()))
      .map(Identifier::getValue)
      .filter(Objects::nonNull)
      .map(String::trim)
      .collect(toCollection(LinkedHashSet::new));
  }

  /**
   * Returns set of identifier ids from cache.
   *
   * @return {@link List} of {@link String} identifier ids that matches names.
   */
  private Set<String> fetchIdentifierIdsFromCache() {
    var identifierTypeIds = referenceDataService.fetchReferenceData(IDENTIFIER_TYPES, CqlQueryParam.NAME,
      getIdentifierNames());
    if (identifierTypeIds.isEmpty()) {
      log.warn("Failed to provide identifiers for [processor: {}]", this.getClass().getSimpleName());
    }
    return identifierTypeIds;
  }
}
