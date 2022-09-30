package org.folio.search.service;

import static org.folio.search.configuration.SearchCacheNames.SEARCH_PREFERENCE_CACHE;

import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class SearchPreferenceService {

  @Cacheable(SEARCH_PREFERENCE_CACHE)
  public String getPreferenceForString(String key) {
    return UUID.randomUUID().toString();
  }
}
