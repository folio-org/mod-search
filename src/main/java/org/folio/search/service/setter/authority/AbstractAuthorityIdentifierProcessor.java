package org.folio.search.service.setter.authority;

import static org.folio.search.client.InventoryReferenceDataClient.ReferenceDataType.AUTHORITY_IDENTIFIER_TYPES;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.Identifier;
import org.folio.search.integration.folio.ReferenceDataService;
import org.folio.search.model.client.CqlQueryParam;
import org.folio.search.service.setter.AbstractIdentifierProcessor;

public abstract class AbstractAuthorityIdentifierProcessor extends AbstractIdentifierProcessor<Authority> {

  private final ReferenceDataService referenceDataService;

  protected AbstractAuthorityIdentifierProcessor(ReferenceDataService referenceDataService) {
    super(referenceDataService);
    this.referenceDataService = referenceDataService;
  }

  @Override
  protected List<Identifier> getIdentifiers(Authority entity) {
    return Optional.ofNullable(entity)
      .map(Authority::getIdentifiers)
      .orElse(Collections.emptyList());
  }

  @Override
  protected Set<String> getIdentifierTypeIds() {
    return referenceDataService.fetchReferenceData(
      AUTHORITY_IDENTIFIER_TYPES,
      CqlQueryParam.CODE,
      getIdentifierNames());
  }
}
