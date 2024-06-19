package org.folio.search.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.awaitility.Durations.ONE_SECOND;
import static org.folio.search.domain.dto.ResourceEventType.CREATE;
import static org.folio.search.domain.dto.ResourceEventType.DELETE;
import static org.folio.search.domain.dto.ResourceEventType.DELETE_ALL;
import static org.folio.search.domain.dto.ResourceEventType.UPDATE;
import static org.folio.search.utils.SearchUtils.INSTITUTION_RESOURCE;
import static org.folio.search.utils.SearchUtils.getIndexName;
import static org.folio.search.utils.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.search.utils.TestConstants.MEMBER_TENANT_ID;
import static org.folio.search.utils.TestConstants.inventoryInstitutionTopic;
import static org.folio.search.utils.TestUtils.kafkaResourceEvent;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.toMap;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.search.builder.SearchSourceBuilder.searchSource;

import java.io.IOException;
import java.util.Objects;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.dto.locationunit.InstitutionDto;
import org.folio.search.support.base.BaseConsortiumIntegrationTest;
import org.folio.spring.testing.type.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.index.query.QueryBuilders;

@Log4j2
@IntegrationTest
class InstitutionsIndexingConsortiumIT extends BaseConsortiumIntegrationTest {

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
    cleanUpIndex(INSTITUTION_RESOURCE, CENTRAL_TENANT_ID);
  }

  @Test
  void shouldIndexAndRemoveInstitution() {
    var institution = institution();
    var createEvent = kafkaResourceEvent(CENTRAL_TENANT_ID, CREATE, toMap(institution), null);
    kafkaTemplate.send(inventoryInstitutionTopic(CENTRAL_TENANT_ID), createEvent);

    awaitAssertInstitutionCount(1);

    var deleteEvent = kafkaResourceEvent(CENTRAL_TENANT_ID, DELETE, null, toMap(institution));
    kafkaTemplate.send(inventoryInstitutionTopic(CENTRAL_TENANT_ID), deleteEvent);

    awaitAssertInstitutionCount(0);
  }

  @Test
  void shouldIndexAndUpdateInstitution() {
    var institution = institution();
    var createEvent = kafkaResourceEvent(CENTRAL_TENANT_ID, CREATE, toMap(institution), null);
    kafkaTemplate.send(inventoryInstitutionTopic(CENTRAL_TENANT_ID), createEvent);

    awaitAssertInstitutionCount(1);

    var institutionUpdated = institution.withName("nameUpdated");
    var updateEvent = kafkaResourceEvent(CENTRAL_TENANT_ID, UPDATE, toMap(institutionUpdated), toMap(institution));
    kafkaTemplate.send(inventoryInstitutionTopic(CENTRAL_TENANT_ID), updateEvent);

    awaitAssertInstitutionCountAfterUpdate(1, institutionUpdated);
  }

  @Test
  void shouldIndexSameInstitutionFromDifferentTenantsAsSeparateDocs() {
    var institution = institution();
    var createCentralEvent = kafkaResourceEvent(CENTRAL_TENANT_ID, CREATE, toMap(institution), null);
    var createMemberEvent = kafkaResourceEvent(MEMBER_TENANT_ID, CREATE, toMap(institution), null);
    kafkaTemplate.send(inventoryInstitutionTopic(CENTRAL_TENANT_ID), createCentralEvent);
    kafkaTemplate.send(inventoryInstitutionTopic(CENTRAL_TENANT_ID), createMemberEvent);

    awaitAssertInstitutionCount(2);
  }

  @Test
  void shouldRemoveAllDocumentsByTenantIdOnDeleteAllEvent() {
    var institution = institution();
    var createCentralEvent = kafkaResourceEvent(CENTRAL_TENANT_ID, CREATE, toMap(institution), null);
    var createMemberEvent = kafkaResourceEvent(MEMBER_TENANT_ID, CREATE, toMap(institution), null);
    kafkaTemplate.send(inventoryInstitutionTopic(CENTRAL_TENANT_ID), createCentralEvent);
    kafkaTemplate.send(inventoryInstitutionTopic(CENTRAL_TENANT_ID), createMemberEvent);

    awaitAssertInstitutionCount(2);

    var deleteAllMemberEvent = new ResourceEvent().type(DELETE_ALL).tenant(MEMBER_TENANT_ID);
    kafkaTemplate.send(inventoryInstitutionTopic(MEMBER_TENANT_ID), deleteAllMemberEvent);

    awaitAssertInstitutionCount(1);
  }

  private static InstitutionDto institution() {
    return InstitutionDto.builder().id(randomId())
      .name("name")
      .code("code")
      .build();
  }

  public static void awaitAssertInstitutionCount(int expected) {
    await().atMost(ONE_MINUTE).pollInterval(ONE_SECOND).untilAsserted(() -> {
      var totalHits = countIndexDocument(INSTITUTION_RESOURCE, CENTRAL_TENANT_ID);

      assertThat(totalHits).isEqualTo(expected);
    });
  }

  public static void awaitAssertInstitutionCountAfterUpdate(int expected, InstitutionDto institutionUpdated) {
    await().atMost(ONE_MINUTE).pollInterval(ONE_SECOND).untilAsserted(() -> {
      var idQuery = QueryBuilders.matchQuery("id", institutionUpdated.getId());
      var nameQuery = QueryBuilders.matchQuery("name", institutionUpdated.getName());

      var searchRequest = new SearchRequest()
        .source(searchSource().query(boolQuery().must(idQuery).must(nameQuery))
          .trackTotalHits(true).from(0).size(1))
        .indices(getIndexName(INSTITUTION_RESOURCE, CENTRAL_TENANT_ID));
      var searchResponse = elasticClient.search(searchRequest, RequestOptions.DEFAULT);
      var hitCount = Objects.requireNonNull(searchResponse.getHits().getTotalHits()).value;

      assertThat(hitCount).isEqualTo(expected);
    });
  }
}
