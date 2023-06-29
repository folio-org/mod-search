package org.folio.search.service.setter.authority;

import org.folio.search.domain.dto.Authority;
import org.folio.search.service.setter.AbstractSharedFieldProcessor;
import org.springframework.stereotype.Component;

@Component
public class AuthoritySharedFieldProcessor extends AbstractSharedFieldProcessor<Authority> {

  @Override
  protected String getSource(Authority eventBody) {
    return eventBody.getSource();
  }
}
