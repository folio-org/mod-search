package org.folio.search.integration;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Optional.ofNullable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.client.InventoryReferenceDataClient.ReferenceDataType.IDENTIFIER_TYPES;
import static org.folio.search.configuration.SearchCacheNames.REFERENCE_DATA_CACHE;
import static org.folio.search.model.service.ReferenceRecord.referenceRecord;
import static org.folio.search.model.service.ResultList.asSinglePage;
import static org.folio.search.utils.TestConstants.INVALID_ISBN_IDENTIFIER_TYPE_ID;
import static org.folio.search.utils.TestConstants.ISBN_IDENTIFIER_TYPE_ID;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestConstants.UNIFORM_ALTERNATIVE_TITLE_ID;
import static org.folio.search.utils.TestUtils.cleanUpCaches;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE;

import feign.FeignException.Forbidden;
import feign.Request;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.folio.search.client.InventoryReferenceDataClient;
import org.folio.search.integration.ReferenceDataServiceTest.TestContextConfiguration;
import org.folio.search.model.client.CqlQuery;
import org.folio.search.model.client.CqlQueryParam;
import org.folio.search.model.service.ReferenceRecord;
import org.folio.search.model.service.ResultList;
import org.folio.spring.DefaultFolioExecutionContext;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.test.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
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
@SpringBootTest(classes = ReferenceDataService.class, webEnvironment = NONE)
class ReferenceDataServiceTest {

  @Autowired
  private CacheManager cacheManager;
  @Autowired
  private ReferenceDataService referenceDataService;
  @MockBean
  private InventoryReferenceDataClient inventoryReferenceDataClient;

  @BeforeEach
  void setUp() {
    cleanUpCaches(cacheManager);
  }

  @Test
  void shouldCacheIdentifierTypeIds() {
    var isbnIdentifierNames = List.of("ISBN", "Invalid ISBN");
    var query = CqlQuery.exactMatchAny(CqlQueryParam.NAME, isbnIdentifierNames);
    when(inventoryReferenceDataClient.getReferenceData(IDENTIFIER_TYPES.getUri(), query, 100))
      .thenReturn(identifiersFetchResponse());

    var actual = referenceDataService.fetchReferenceData(IDENTIFIER_TYPES, CqlQueryParam.NAME, isbnIdentifierNames);
    var expectedIdentifiers = Set.of(ISBN_IDENTIFIER_TYPE_ID, INVALID_ISBN_IDENTIFIER_TYPE_ID);

    assertThat(actual).isEqualTo(expectedIdentifiers);

    var cachedValue = getCachedValue(TENANT_ID + ":ISBN,Invalid ISBN:identifier_types:name");
    assertThat(cachedValue).isPresent().get().isEqualTo(expectedIdentifiers);
  }

  @Test
  void getReferenceData_negative_exceptionalResponseFromReferenceDataClient() {
    var isbnIdentifierNames = List.of("ISBN", "Invalid ISBN");
    var query = CqlQuery.exactMatchAny(CqlQueryParam.NAME, isbnIdentifierNames);
    var request = mock(Request.class);
    when(inventoryReferenceDataClient.getReferenceData(IDENTIFIER_TYPES.getUri(), query, 100))
      .thenThrow(new Forbidden("invalid permission", request, null, emptyMap()));

    var actual = referenceDataService.fetchReferenceData(IDENTIFIER_TYPES, CqlQueryParam.NAME, isbnIdentifierNames);

    assertThat(actual).isEmpty();
    var cachedValue = getCachedValue(TENANT_ID + ":ISBN,Invalid ISBN:identifier_types:name");
    assertThat(cachedValue).isEmpty();
  }

  @Test
  void fetchAlternativeTitleIds_positive() {
    var isbnIdentifierNames = List.of("Uniform Title");
    var query = CqlQuery.exactMatchAny(CqlQueryParam.NAME, isbnIdentifierNames);
    when(inventoryReferenceDataClient.getReferenceData(IDENTIFIER_TYPES.getUri(), query, 100))
      .thenReturn(alternativeTitlesTypesFetchResponse());

    var actual = referenceDataService.fetchReferenceData(IDENTIFIER_TYPES, CqlQueryParam.NAME, isbnIdentifierNames);
    var expectedIdentifiers = singleton(UNIFORM_ALTERNATIVE_TITLE_ID);

    assertThat(actual).isEqualTo(expectedIdentifiers);

    var cachedValue = getCachedValue(TENANT_ID + ":Uniform Title:identifier_types:name");
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

  private Optional<Object> getCachedValue(String cacheKey) {
    return ofNullable(cacheManager.getCache(REFERENCE_DATA_CACHE))
      .map(cache -> cache.get(cacheKey))
      .map(ValueWrapper::get);
  }

  @EnableCaching
  @TestConfiguration
  static class TestContextConfiguration {

    @Bean
    CacheManager cacheManager() {
      return new ConcurrentMapCacheManager(REFERENCE_DATA_CACHE);
    }

    @Bean
    FolioExecutionContext folioExecutionContext() {
      return new DefaultFolioExecutionContext(null, mapOf(TENANT, singletonList(TENANT_ID)));
    }
  }
}
