package org.folio.search.service.setter.instance;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.model.service.ResultList.asSinglePage;
import static org.folio.spring.scope.FolioExecutionScopeExecutionContextManager.beginFolioExecutionContext;
import static org.folio.spring.scope.FolioExecutionScopeExecutionContextManager.endFolioExecutionContext;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.folio.search.SearchApplication;
import org.folio.search.client.IdentifierTypeClient;
import org.folio.search.model.service.ReferenceRecord;
import org.folio.search.model.service.ResultList;
import org.folio.search.repository.cache.InstanceIdentifierTypeCache;
import org.folio.search.service.context.FolioExecutionContextBuilder;
import org.folio.search.utils.types.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;

@IntegrationTest
@SpringBootTest(classes = SearchApplication.class)
@EnableAutoConfiguration
@EnableCaching
class InstanceIdentifierTypeCacheIT {
  private static final String ISBN_IDENTIFIER = randomUUID().toString();
  private static final String INVALID_ISBN_IDENTIFIER = randomUUID().toString();

  @MockBean private IdentifierTypeClient identifierTypeClient;
  @Autowired private CacheManager cacheManager;
  @Autowired private InstanceIdentifierTypeCache cache;
  @Autowired private FolioExecutionContextBuilder contextBuilder;

  @Test
  @SuppressWarnings("unchecked")
  void shouldCacheIdentifierTypeIds() {
    when(identifierTypeClient.getIdentifierTypes(any())).thenReturn(referenceRecords());

    beginFolioExecutionContext(contextBuilder.dbOnlyContext("my_tenant_name"));
    cache.fetchIdentifierIds(List.of("ISBN", "Invalid ISBN"));
    endFolioExecutionContext();

    var cache = cacheManager.getCache(InstanceIdentifierTypeCache.CACHE_NAME);
    assertThat(cache).isNotNull();
    Set<String> cachedValue = (Set<String>) cache.get("my_tenant_name: ISBN,Invalid ISBN", Set.class);

    assertThat(cachedValue)
      .containsExactlyInAnyOrder(ISBN_IDENTIFIER, INVALID_ISBN_IDENTIFIER);
  }

  private ResultList<ReferenceRecord> referenceRecords() {
    return asSinglePage(
      ReferenceRecord.referenceRecord(ISBN_IDENTIFIER, "ISBN"),
      ReferenceRecord.referenceRecord(INVALID_ISBN_IDENTIFIER, "Invalid ISBN"));
  }
}
