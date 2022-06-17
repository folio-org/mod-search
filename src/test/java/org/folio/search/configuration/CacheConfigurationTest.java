package org.folio.search.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.folio.search.configuration.properties.SearchCacheConfigurationProperties;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class CacheConfigurationTest {

  @InjectMocks
  private CacheConfiguration cacheConfiguration;
  @Mock
  private SearchCacheConfigurationProperties cacheConfigurationProperties;

  @Test
  void createCallNumberRangesCache() {
    when(cacheConfigurationProperties.getCallNumberBrowseRangesCacheSpec()).thenReturn("expireAfterAccess=5m");
    var cache = cacheConfiguration.callNumberRangesCache(cacheConfigurationProperties);
    assertThat(cache).isNotNull();
  }
}
