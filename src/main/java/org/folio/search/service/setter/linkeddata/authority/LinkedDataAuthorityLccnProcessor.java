package org.folio.search.service.setter.linkeddata.authority;

import java.util.Set;
import org.folio.search.domain.dto.LinkedDataAuthority;
import org.folio.search.service.lccn.LccnNormalizer;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.service.setter.linkeddata.common.LinkedDataLccnProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LinkedDataAuthorityLccnProcessor implements FieldProcessor<LinkedDataAuthority, Set<String>> {

  private final LinkedDataLccnProcessor linkedDataLccnProcessor;

  public LinkedDataAuthorityLccnProcessor(@Autowired LccnNormalizer lccnNormalizer) {
    this.linkedDataLccnProcessor = new LinkedDataLccnProcessor(lccnNormalizer);
  }

  @Override
  public Set<String> getFieldValue(LinkedDataAuthority linkedDataAuthority) {
    return linkedDataLccnProcessor.getFieldValue(linkedDataAuthority.getIdentifiers());
  }
}
