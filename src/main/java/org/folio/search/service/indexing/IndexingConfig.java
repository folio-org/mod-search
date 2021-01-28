package org.folio.search.service.indexing;

import static java.util.Set.copyOf;

import java.util.Set;

public final class IndexingConfig {
  private final Set<String> tenantSupportedLanguages;

  public IndexingConfig(Set<String> tenantSupportedLanguages) {
    this.tenantSupportedLanguages = copyOf(tenantSupportedLanguages);
  }

  public boolean isSupportedLanguage(String code) {
    return tenantSupportedLanguages.contains(code);
  }
}
