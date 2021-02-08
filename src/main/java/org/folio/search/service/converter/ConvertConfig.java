package org.folio.search.service.converter;

import java.util.Set;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;

public class ConvertConfig {
  private final MultiValuedMap<String, String> tenantSupportedLanguages = new HashSetValuedHashMap<>();

  public ConvertConfig addSupportedLanguage(String tenant, Set<String> languageCodes) {
    tenantSupportedLanguages.putAll(tenant, languageCodes);
    return this;
  }

  public boolean isSupportedLanguageCode(String tenant, String code) {
    return tenantSupportedLanguages.get(tenant).contains(code);
  }
}
