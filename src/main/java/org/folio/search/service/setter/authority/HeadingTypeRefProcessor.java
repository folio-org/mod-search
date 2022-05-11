package org.folio.search.service.setter.authority;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.folio.search.model.metadata.AuthorityFieldDescription;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HeadingTypeRefProcessor extends AbstractAuthorityProcessor {

  @Override
  public String getFieldValue(Map<String, Object> eventBody) {
    return getAuthorityType(eventBody, AuthorityFieldDescription::getHeadingTypeExt, null);
  }
}
