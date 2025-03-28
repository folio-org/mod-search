package org.folio.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.awaitility.Durations.ONE_SECOND;
import static org.folio.search.domain.dto.ResourceEventType.CREATE;
import static org.folio.search.domain.dto.ResourceEventType.DELETE;
import static org.folio.search.domain.dto.ResourceEventType.DELETE_ALL;
import static org.folio.search.domain.dto.ResourceEventType.UPDATE;
import static org.folio.search.utils.SearchUtils.getIndexName;
import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.folio.support.TestConstants.inventoryLibraryTopic;
import static org.folio.support.utils.JsonTestUtils.toMap;
import static org.folio.support.utils.TestUtils.kafkaResourceEvent;
import static org.folio.support.utils.TestUtils.randomId;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.search.builder.SearchSourceBuilder.searchSource;

import java.io.IOException;
import java.util.Objects;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.dto.locationunit.LibraryDto;
import org.folio.search.model.types.ResourceType;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.base.BaseConsortiumIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.index.query.QueryBuilders;

@Log4j2
@IntegrationTest
class IndexingLibrariesConsortiumIT extends BaseConsortiumIntegrationTest {

  @BeforeAll
  static void prepare() {
    setUpTenant(CENTRAL_TENANT_ID);
    setUpTenant(MEMBER_TENANT_ID);
  }

  @AfterAll
  static void cleanUp() {
    removeTenant(CENTRAL_TENANT_ID);
    removeTenant(MEMBER_TENANT_ID);
  }

  @AfterEach
  void tearDown() throws IOException {
    cleanUpIndex(ResourceType.LIBRARY, CENTRAL_TENANT_ID);
  }

  @Test
  void shouldIndexAndRemoveLibrary() {
    var library = library();
    var createEvent = kafkaResourceEvent(CENTRAL_TENANT_ID, CREATE, toMap(library), null);
    kafkaTemplate.send(inventoryLibraryTopic(CENTRAL_TENANT_ID), createEvent);

    awaitAssertLibraryCount(1);

    var deleteEvent = kafkaResourceEvent(CENTRAL_TENANT_ID, DELETE, null, toMap(library));
    kafkaTemplate.send(inventoryLibraryTopic(CENTRAL_TENANT_ID), deleteEvent);

    awaitAssertLibraryCount(0);
  }

  @Test
  void shouldIndexAndUpdateLibrary() {
    var library = library();
    var createEvent = kafkaResourceEvent(CENTRAL_TENANT_ID, CREATE, toMap(library), null);
    kafkaTemplate.send(inventoryLibraryTopic(CENTRAL_TENANT_ID), createEvent);

    awaitAssertLibraryCount(1);

    var libraryUpdated = library.withName("nameUpdated");
    var updateEvent = kafkaResourceEvent(CENTRAL_TENANT_ID, UPDATE, toMap(libraryUpdated), toMap(library));
    kafkaTemplate.send(inventoryLibraryTopic(CENTRAL_TENANT_ID), updateEvent);

    awaitAssertLibraryCountAfterUpdate(1, libraryUpdated);
  }

  @Test
  void shouldIndexSameLibraryFromDifferentTenantsAsSeparateDocs() {
    var library = library();
    var createCentralEvent = kafkaResourceEvent(CENTRAL_TENANT_ID, CREATE, toMap(library), null);
    var createMemberEvent = kafkaResourceEvent(MEMBER_TENANT_ID, CREATE, toMap(library), null);
    kafkaTemplate.send(inventoryLibraryTopic(CENTRAL_TENANT_ID), createCentralEvent);
    kafkaTemplate.send(inventoryLibraryTopic(CENTRAL_TENANT_ID), createMemberEvent);

    awaitAssertLibraryCount(2);
  }

  @Test
  void shouldRemoveAllDocumentsByTenantIdOnDeleteAllEvent() {
    var library = library();
    var createCentralEvent = kafkaResourceEvent(CENTRAL_TENANT_ID, CREATE, toMap(library), null);
    var createMemberEvent = kafkaResourceEvent(MEMBER_TENANT_ID, CREATE, toMap(library), null);
    kafkaTemplate.send(inventoryLibraryTopic(CENTRAL_TENANT_ID), createCentralEvent);
    kafkaTemplate.send(inventoryLibraryTopic(CENTRAL_TENANT_ID), createMemberEvent);

    awaitAssertLibraryCount(2);

    var deleteAllMemberEvent = new ResourceEvent().type(DELETE_ALL).tenant(MEMBER_TENANT_ID);
    kafkaTemplate.send(inventoryLibraryTopic(MEMBER_TENANT_ID), deleteAllMemberEvent);

    awaitAssertLibraryCount(1);
  }

  public static void awaitAssertLibraryCount(int expected) {
    await().atMost(ONE_MINUTE).pollInterval(ONE_SECOND).untilAsserted(() -> {
      var totalHits = countIndexDocument(ResourceType.LIBRARY, CENTRAL_TENANT_ID);

      assertThat(totalHits).isEqualTo(expected);
    });
  }

  public static void awaitAssertLibraryCountAfterUpdate(int expected, LibraryDto libraryUpdated) {
    await().atMost(ONE_MINUTE).pollInterval(ONE_SECOND).untilAsserted(() -> {
      var idQuery = QueryBuilders.matchQuery("id", libraryUpdated.getId());
      var nameQuery = QueryBuilders.matchQuery("name", libraryUpdated.getName());

      var searchRequest = new SearchRequest()
        .source(searchSource().query(boolQuery().must(idQuery).must(nameQuery))
          .trackTotalHits(true).from(0).size(1))
        .indices(getIndexName(ResourceType.LIBRARY, CENTRAL_TENANT_ID));
      var searchResponse = elasticClient.search(searchRequest, RequestOptions.DEFAULT);
      var hitCount = Objects.requireNonNull(searchResponse.getHits().getTotalHits()).value;

      assertThat(hitCount).isEqualTo(expected);
    });
  }

  private static LibraryDto library() {
    return LibraryDto.builder().id(randomId())
      .name("name")
      .code("code")
      .build();
  }
}
