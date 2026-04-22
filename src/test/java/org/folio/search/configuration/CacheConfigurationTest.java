package org.folio.search.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.folio.search.configuration.properties.CacheConfigurationProperties;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.cache.CacheType;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.NoOpCacheManager;

@UnitTest
@ExtendWith(MockitoExtension.class)
class CacheConfigurationTest {

  @InjectMocks
  private CacheConfiguration cacheConfiguration;
  @Mock
  private CacheConfigurationProperties cacheProperties;

  @Test
  void cacheManager_returnsCaffeineCacheManager_whenCacheTypeIsCaffeine() {
    when(cacheProperties.getType()).thenReturn(CacheType.CAFFEINE);
    when(cacheProperties.getCacheNames()).thenReturn(null);
    when(cacheProperties.getCaffeine()).thenReturn(new CacheConfigurationProperties.Caffeine());

    var result = cacheConfiguration.cacheManager();

    assertThat(result).isInstanceOf(CaffeineCacheManager.class);
  }

  @Test
  void cacheManager_returnsNoOpCacheManager_whenCacheTypeIsNone() {
    when(cacheProperties.getType()).thenReturn(CacheType.NONE);

    var result = cacheConfiguration.cacheManager();

    assertThat(result).isInstanceOf(NoOpCacheManager.class);
  }
}

