package org.folio.search.configuration;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfiguration {

  public static final String ES_INDICES_CACHE = "es-indices";
  public static final String IDENTIFIER_IDS_CACHE = "identifier-types";
  public static final String ALTERNATIVE_TITLE_TYPES_CACHE = "alternative-title-types";
  public static final String SYSTEM_USER_CACHE = "system-user-cache";
}
