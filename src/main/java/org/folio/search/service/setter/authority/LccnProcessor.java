package org.folio.search.service.setter.authority;

import static org.folio.search.utils.CollectionUtils.toLinkedHashSet;
import static org.folio.search.utils.CollectionUtils.toStreamSafe;

import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.AuthorityIdentifiers;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LccnProcessor implements FieldProcessor<Authority, Set<String>> {

  private static final List<String> SEARCHABLE_IDENTIFIERS =
    List.of("Control number", "LCCN", "Other standard identifier", "System control number");

  @Override
  public Set<String> getFieldValue(Authority authority) {
    return toStreamSafe(authority.getIdentifiers())
      .map(AuthorityIdentifiers::getValue)
      .filter(SEARCHABLE_IDENTIFIERS::contains)
      .collect(toLinkedHashSet());
  }
}
