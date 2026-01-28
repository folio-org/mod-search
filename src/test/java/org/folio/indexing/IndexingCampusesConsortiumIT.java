package org.folio.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.awaitility.Durations.ONE_SECOND;
import static org.folio.search.domain.dto.ResourceEventType.CREATE;
import static org.folio.search.domain.dto.ResourceEventType.DELETE;
import static org.folio.search.domain.dto.ResourceEventType.DELETE_ALL;
import static org.folio.search.domain.dto.ResourceEventType.UPDATE;
import static org.folio.search.model.types.ResourceType.CAMPUS;
import static org.folio.search.utils.SearchUtils.getIndexName;
import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.folio.support.TestConstants.inventoryCampusTopic;
import static org.folio.support.utils.JsonTestUtils.toMap;
import static org.folio.support.utils.TestUtils.kafkaResourceEvent;
import static org.folio.support.utils.TestUtils.randomId;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.search.builder.SearchSourceBuilder.searchSource;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.integration.message.KafkaMessageListener;
import org.folio.search.model.dto.locationunit.CampusDto;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.base.BaseConsortiumIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.index.query.QueryBuilders;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@Log4j2
@IntegrationTest
class IndexingCampusesConsortiumIT extends BaseConsortiumIntegrationTest {

  @MockitoSpyBean private KafkaMessageListener kafkaMessageListener;

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
    cleanUpIndex(CAMPUS, CENTRAL_TENANT_ID);
    reset(kafkaMessageListener);
  }

  @Test
  void shouldIndexAndRemoveCampus() {
    var campus = campus();
    var createEvent = kafkaResourceEvent(CENTRAL_TENANT_ID, CREATE, toMap(campus), null);
    kafkaTemplate.send(inventoryCampusTopic(CENTRAL_TENANT_ID), createEvent);

    awaitAssertCampusCount(1);

    var deleteEvent = kafkaResourceEvent(CENTRAL_TENANT_ID, DELETE, null, toMap(campus));
    kafkaTemplate.send(inventoryCampusTopic(CENTRAL_TENANT_ID), deleteEvent);

    awaitAssertCampusCount(0);
  }

  @Test
  void shouldIndexAndUpdateCampus() {
    var campus = campus();
    var createEvent = kafkaResourceEvent(CENTRAL_TENANT_ID, CREATE, toMap(campus), null);
    kafkaTemplate.send(inventoryCampusTopic(CENTRAL_TENANT_ID), createEvent);

    awaitAssertCampusCount(1);

    var campusUpdated = campus.withName("nameUpdated");
    var updateEvent = kafkaResourceEvent(CENTRAL_TENANT_ID, UPDATE, toMap(campusUpdated), toMap(campus));
    kafkaTemplate.send(inventoryCampusTopic(CENTRAL_TENANT_ID), updateEvent);

    awaitAssertCampusCountAfterUpdate(1, campusUpdated);
  }

  @Test
  void shouldIndexSameCampusFromDifferentTenantsAsSeparateDocs() {
    var campus = campus();
    var createCentralEvent = kafkaResourceEvent(CENTRAL_TENANT_ID, CREATE, toMap(campus), null);
    var createMemberEvent = kafkaResourceEvent(MEMBER_TENANT_ID, CREATE, toMap(campus), null);
    kafkaTemplate.send(inventoryCampusTopic(CENTRAL_TENANT_ID), createCentralEvent);
    kafkaTemplate.send(inventoryCampusTopic(CENTRAL_TENANT_ID), createMemberEvent);

    awaitAssertCampusCount(2);
  }

  @Test
  void shouldRemoveAllDocumentsByTenantIdOnDeleteAllEvent() {
    var campus = campus();
    var createCentralEvent = kafkaResourceEvent(CENTRAL_TENANT_ID, CREATE, toMap(campus), null);
    var createMemberEvent = kafkaResourceEvent(MEMBER_TENANT_ID, CREATE, toMap(campus), null);
    kafkaTemplate.send(inventoryCampusTopic(CENTRAL_TENANT_ID), createCentralEvent);
    kafkaTemplate.send(inventoryCampusTopic(CENTRAL_TENANT_ID), createMemberEvent);

    awaitAssertCampusCount(2);

    var deleteAllMemberEvent = new ResourceEvent().type(DELETE_ALL).tenant(MEMBER_TENANT_ID);
    kafkaTemplate.send(inventoryCampusTopic(MEMBER_TENANT_ID), deleteAllMemberEvent);

    awaitAssertCampusCount(1);
  }

  @Test
  void shouldIgnoreShadowInstitution() {
    var libraryMap = toMap(campus());
    libraryMap.put("isShadow", true);
    var visited = new AtomicBoolean(false);

    doAnswer(inv -> {
      visited.set(true);
      return inv.callRealMethod();
    }).when(kafkaMessageListener).handleLocationEvents(anyList());

    var libraryEvent = kafkaResourceEvent(CENTRAL_TENANT_ID, CREATE, libraryMap, null);
    kafkaTemplate.send(inventoryCampusTopic(MEMBER_TENANT_ID), libraryEvent);

    await().atMost(ONE_MINUTE)
      .pollInterval(ONE_HUNDRED_MILLISECONDS)
      .untilAsserted(() -> assertThat(visited.get()).isTrue());

    awaitAssertCampusCount(0);
  }

  public static void awaitAssertCampusCount(int expected) {
    await().atMost(ONE_MINUTE).pollInterval(ONE_SECOND).untilAsserted(() -> {
      var totalHits = countIndexDocument(CAMPUS, CENTRAL_TENANT_ID);

      assertThat(totalHits).isEqualTo(expected);
    });
  }

  public static void awaitAssertCampusCountAfterUpdate(int expected, CampusDto campusUpdated) {
    await().atMost(ONE_MINUTE).pollInterval(ONE_SECOND).untilAsserted(() -> {
      var idQuery = QueryBuilders.matchQuery("id", campusUpdated.getId());
      var nameQuery = QueryBuilders.matchQuery("name", campusUpdated.getName());

      var searchRequest = new SearchRequest()
        .source(searchSource().query(boolQuery().must(idQuery).must(nameQuery))
          .trackTotalHits(true).from(0).size(1))
        .indices(getIndexName(CAMPUS, CENTRAL_TENANT_ID));
      var searchResponse = elasticClient.search(searchRequest, RequestOptions.DEFAULT);
      var hitCount = Objects.requireNonNull(searchResponse.getHits().getTotalHits()).value();

      assertThat(hitCount).isEqualTo(expected);
    });
  }

  private static CampusDto campus() {
    return CampusDto.builder().id(randomId())
      .name("name")
      .code("code")
      .build();
  }
}
