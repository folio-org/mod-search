package org.folio.search.service.setter.instance;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Optional.ofNullable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.configuration.SearchCacheNames.ALTERNATIVE_TITLE_TYPES_CACHE;
import static org.folio.search.configuration.SearchCacheNames.IDENTIFIER_IDS_CACHE;
import static org.folio.search.model.service.ReferenceRecord.referenceRecord;
import static org.folio.search.model.service.ResultList.asSinglePage;
import static org.folio.search.utils.TestConstants.INVALID_ISBN_IDENTIFIER_TYPE_ID;
import static org.folio.search.utils.TestConstants.ISBN_IDENTIFIER_TYPE_ID;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestConstants.UNIFORM_ALTERNATIVE_TITLE_ID;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.folio.search.client.AlternativeTitleTypesClient;
import org.folio.search.client.IdentifierTypeClient;
import org.folio.search.client.cql.CqlQuery;
import org.folio.search.model.service.ReferenceRecord;
import org.folio.search.model.service.ResultList;
import org.folio.search.repository.cache.InstanceReferenceDataService;
import org.folio.search.service.setter.instance.InstanceReferenceDataServiceTest.TestContextConfiguration;
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
@SpringBootTest(classes = InstanceReferenceDataService.class, webEnvironment = NONE)
class InstanceReferenceDataServiceTest {

  @Autowired private CacheManager cacheManager;
  @Autowired private InstanceReferenceDataService referenceDataService;
  @MockBean private IdentifierTypeClient identifierTypeClient;
  @MockBean private AlternativeTitleTypesClient alternativeTitleTypesClient;

  @Test
  void shouldCacheIdentifierTypeIds() {
    var isbnIdentifierNames = List.of("ISBN", "Invalid ISBN");
    var query = CqlQuery.exactMatchAny("name", isbnIdentifierNames);
    when(identifierTypeClient.getIdentifierTypes(query)).thenReturn(identifiersFetchResponse());

    var actual = referenceDataService.fetchIdentifierIds(isbnIdentifierNames);
    var expectedIdentifiers = Set.of(ISBN_IDENTIFIER_TYPE_ID, INVALID_ISBN_IDENTIFIER_TYPE_ID);

    assertThat(actual).isEqualTo(expectedIdentifiers);

    var cachedValue = getCachedValue(IDENTIFIER_IDS_CACHE, TENANT_ID + ":ISBN,Invalid ISBN");
    assertThat(cachedValue).isPresent().get().isEqualTo(expectedIdentifiers);
  }

  @Test
  void fetchAlternativeTitleIds_positive() {
    var isbnIdentifierNames = List.of("Uniform Title");
    var query = CqlQuery.exactMatchAny("name", isbnIdentifierNames);
    when(alternativeTitleTypesClient.getAlternativeTitleTypes(query)).thenReturn(alternativeTitlesTypesFetchResponse());

    var actual = referenceDataService.fetchAlternativeTitleIds(isbnIdentifierNames);
    var expectedIdentifiers = singleton(UNIFORM_ALTERNATIVE_TITLE_ID);

    assertThat(actual).isEqualTo(expectedIdentifiers);

    var cachedValue = getCachedValue(ALTERNATIVE_TITLE_TYPES_CACHE, TENANT_ID + ":Uniform Title");
    assertThat(cachedValue).isPresent().get().isEqualTo(expectedIdentifiers);
  }

  private static ResultList<ReferenceRecord> identifiersFetchResponse() {
    return asSinglePage(
      referenceRecord(ISBN_IDENTIFIER_TYPE_ID, "ISBN"),
      referenceRecord(INVALID_ISBN_IDENTIFIER_TYPE_ID, "Invalid ISBN"));
  }

  private static ResultList<ReferenceRecord> alternativeTitlesTypesFetchResponse() {
    return asSinglePage(referenceRecord(UNIFORM_ALTERNATIVE_TITLE_ID, "Uniform Title"));
  }

  private Optional<Object> getCachedValue(String cacheName, String cacheKey) {
    return ofNullable(cacheManager.getCache(cacheName))
      .map(cache -> cache.get(cacheKey))
      .map(ValueWrapper::get);
  }

  @EnableCaching
  @TestConfiguration
  static class TestContextConfiguration {

    @Bean
    CacheManager cacheManager() {
      return new ConcurrentMapCacheManager(IDENTIFIER_IDS_CACHE, ALTERNATIVE_TITLE_TYPES_CACHE);
    }

    @Bean
    FolioExecutionContext folioExecutionContext() {
      return new DefaultFolioExecutionContext(null, mapOf(TENANT, singletonList(TENANT_ID)));
    }
  }
}
