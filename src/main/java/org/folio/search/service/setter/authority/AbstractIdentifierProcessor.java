package org.folio.search.service.setter.authority;

import static java.util.stream.Collectors.toList;
import static org.folio.search.utils.CollectionUtils.toStreamSafe;

import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.AuthorityIdentifiers;
import org.folio.search.integration.InstanceReferenceDataService;
import org.folio.search.service.setter.FieldProcessor;

@RequiredArgsConstructor
public abstract class AbstractIdentifierProcessor implements FieldProcessor<Authority, Set<String>> {

  private final InstanceReferenceDataService identifierTypeCache;

  /**
   * Returns authority identifiers from event body by specified set of types.
   *
   * @param authority event body as map to process
   * @return {@link List} of {@link AuthorityIdentifiers} objects
   */
  protected List<AuthorityIdentifiers> getAuthorityIdentifiers(Authority authority) {
    var identifierTypeIds = identifierTypeCache.fetchIdentifierIds(getIdentifierNames());
    return toStreamSafe(authority.getIdentifiers())
      .filter(identifier -> identifierTypeIds.contains(identifier.getIdentifierTypeId()))
      .collect(toList());
  }

  /**
   * Returns set of identifier names, which will be used in method {@link #getAuthorityIdentifiers(Authority)}.
   *
   * @return {@link List} of {@link String} authority identifier type names.
   */
  protected abstract List<String> getIdentifierNames();
}
