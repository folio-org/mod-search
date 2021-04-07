package org.folio.search.service.setter.instance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.model.service.ResultList.asSinglePage;
import static org.folio.search.service.setter.instance.IsbnProcessor.ISBN_IDENTIFIER_NAMES;
import static org.folio.search.utils.TestConstants.INVALID_ISBN_IDENTIFIER_TYPE_ID;
import static org.folio.search.utils.TestConstants.ISBN_IDENTIFIER_TYPE_ID;
import static org.folio.spring.scope.FolioExecutionScopeExecutionContextManager.beginFolioExecutionContext;
import static org.folio.spring.scope.FolioExecutionScopeExecutionContextManager.endFolioExecutionContext;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.folio.search.SearchApplication;
import org.folio.search.client.IdentifierTypeClient;
import org.folio.search.model.service.ReferenceRecord;
import org.folio.search.model.service.ResultList;
import org.folio.search.repository.cache.InstanceIdentifierTypeCache;
import org.folio.search.service.context.FolioExecutionContextBuilder;
import org.folio.search.support.extension.EnablePostgres;
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
@EnablePostgres
class InstanceIdentifierTypeCacheIT {
  @MockBean private IdentifierTypeClient identifierTypeClient;
  @Autowired private CacheManager cacheManager;
  @Autowired private InstanceIdentifierTypeCache cache;
  @Autowired private FolioExecutionContextBuilder contextBuilder;

  @Test
  @SuppressWarnings("unchecked")
  void shouldCacheIdentifierTypeIds() {
    when(identifierTypeClient.getIdentifierTypes(any())).thenReturn(referenceRecords());

    beginFolioExecutionContext(contextBuilder.dbOnlyContext("my_tenant_name"));
    cache.fetchIdentifierIds(ISBN_IDENTIFIER_NAMES);
    endFolioExecutionContext();

    var cache = cacheManager.getCache(InstanceIdentifierTypeCache.CACHE_NAME);
    assertThat(cache).isNotNull();
    Set<String> cachedValue = (Set<String>) cache.get("my_tenant_name: ISBN,Invalid ISBN", Set.class);

    assertThat(cachedValue)
      .containsExactlyInAnyOrder(ISBN_IDENTIFIER_TYPE_ID, INVALID_ISBN_IDENTIFIER_TYPE_ID);
  }

  private ResultList<ReferenceRecord> referenceRecords() {
    return asSinglePage(
      ReferenceRecord.referenceRecord(ISBN_IDENTIFIER_TYPE_ID, "ISBN"),
      ReferenceRecord.referenceRecord(INVALID_ISBN_IDENTIFIER_TYPE_ID, "Invalid ISBN"));
  }
}
