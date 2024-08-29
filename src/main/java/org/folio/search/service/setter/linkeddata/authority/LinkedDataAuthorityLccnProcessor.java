package org.folio.search.service.setter.linkeddata.authority;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.LinkedDataAuthority;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.service.setter.linkeddata.common.LinkedDataLccnProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LinkedDataAuthorityLccnProcessor implements FieldProcessor<LinkedDataAuthority, Set<String>> {

  private final LinkedDataLccnProcessor linkedDataLccnProcessor;

  @Override
  public Set<String> getFieldValue(LinkedDataAuthority linkedDataAuthority) {
    return linkedDataLccnProcessor.getFieldValue(linkedDataAuthority.getIdentifiers());
  }
}
