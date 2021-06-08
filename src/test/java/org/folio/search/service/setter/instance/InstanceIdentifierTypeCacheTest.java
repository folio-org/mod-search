package org.folio.search.service.setter.instance;

import static java.util.Collections.singletonList;
import static java.util.Optional.ofNullable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.model.service.ResultList.asSinglePage;
import static org.folio.search.repository.cache.InstanceIdentifierTypeCache.CACHE_NAME;
import static org.folio.search.service.setter.instance.IsbnProcessor.ISBN_IDENTIFIER_NAMES;
import static org.folio.search.utils.TestConstants.INVALID_ISBN_IDENTIFIER_TYPE_ID;
import static org.folio.search.utils.TestConstants.ISBN_IDENTIFIER_TYPE_ID;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE;

import java.util.Optional;
import java.util.Set;
import org.folio.search.client.IdentifierTypeClient;
import org.folio.search.model.service.ReferenceRecord;
import org.folio.search.model.service.ResultList;
import org.folio.search.repository.cache.InstanceIdentifierTypeCache;
import org.folio.search.service.setter.instance.InstanceIdentifierTypeCacheTest.TestContextConfiguration;
import org.folio.search.utils.types.UnitTest;
import org.folio.spring.DefaultFolioExecutionContext;
import org.folio.spring.FolioExecutionContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.Cache.ValueWrapper;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@UnitTest
@Import(TestContextConfiguration.class)
@SpringBootTest(classes = InstanceIdentifierTypeCache.class, webEnvironment = NONE)
class InstanceIdentifierTypeCacheTest {

  @Autowired private CacheManager cacheManager;
  @Autowired private InstanceIdentifierTypeCache cache;
  @MockBean private IdentifierTypeClient identifierTypeClient;

  @Test
  void shouldCacheIdentifierTypeIds() {
    when(identifierTypeClient.getIdentifierTypes(any())).thenReturn(referenceRecords());
    var expectedIdentifierIds = Set.of(ISBN_IDENTIFIER_TYPE_ID, INVALID_ISBN_IDENTIFIER_TYPE_ID);

    var actual = cache.fetchIdentifierIds(ISBN_IDENTIFIER_NAMES);
    assertThat(actual).isEqualTo(expectedIdentifierIds);

    var cachedValue = getCachedValue();
    assertThat(cachedValue).isPresent().get().isEqualTo(expectedIdentifierIds);
  }

  private ResultList<ReferenceRecord> referenceRecords() {
    return asSinglePage(
      ReferenceRecord.referenceRecord(ISBN_IDENTIFIER_TYPE_ID, "ISBN"),
      ReferenceRecord.referenceRecord(INVALID_ISBN_IDENTIFIER_TYPE_ID, "Invalid ISBN"));
  }

  private Optional<Object> getCachedValue() {
    return ofNullable(cacheManager.getCache(InstanceIdentifierTypeCache.CACHE_NAME))
      .map(cache -> cache.get(TENANT_ID + ": ISBN,Invalid ISBN"))
      .map(ValueWrapper::get);
  }

  @EnableCaching
  @TestConfiguration
  static class TestContextConfiguration {

    @Bean
    CacheManager cacheManager() {
      return new ConcurrentMapCacheManager(CACHE_NAME);
    }

    @Bean
    FolioExecutionContext folioExecutionContext() {
      return new DefaultFolioExecutionContext(null, mapOf(TENANT, singletonList(TENANT_ID)));
    }
  }
}
