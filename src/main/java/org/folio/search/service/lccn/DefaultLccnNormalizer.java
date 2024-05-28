package org.folio.search.service.lccn;

import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class DefaultLccnNormalizer implements LccnNormalizer {

  @Override
  public Optional<String> apply(String lccn) {
    if (StringUtils.isBlank(lccn)) {
      return Optional.empty();
    }

    return Optional.of(StringUtils.deleteWhitespace(lccn))
      .map(String::toLowerCase);
  }
}
