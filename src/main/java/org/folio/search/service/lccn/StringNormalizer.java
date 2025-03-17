package org.folio.search.service.lccn;

import java.util.Optional;
import java.util.function.Function;

public interface StringNormalizer extends Function<String, Optional<String>> {
}
