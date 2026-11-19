package org.folio.indexing;

import static org.folio.search.domain.dto.ResourceEventType.CREATE;
import static org.folio.search.domain.dto.ResourceEventType.DELETE;
import static org.folio.search.domain.dto.ResourceEventType.DELETE_ALL;
import static org.folio.search.domain.dto.ResourceEventType.UPDATE;
import static org.folio.search.model.types.ResourceType.CAMPUS;
import static org.folio.search.model.types.ResourceType.INSTITUTION;
import static org.folio.search.model.types.ResourceType.LIBRARY;
import static org.folio.search.model.types.ResourceType.LOCATION;
import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.folio.support.utils.JsonTestUtils.toMap;
import static org.folio.support.utils.TestUtils.randomId;
import static org.folio.support.utils.TestUtils.resourceEvent;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.index.query.QueryBuilders.matchQuery;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.folio.search.domain.dto.Metadata;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.dto.location.BaseLocationDto;
import org.folio.search.model.dto.location.CampusDto;
import org.folio.search.model.dto.location.InstitutionDto;
import org.folio.search.model.dto.location.LibraryDto;
import org.folio.search.model.dto.location.LocationDto;
import org.folio.search.model.types.ResourceType;
import org.folio.support.TestConstants;
import org.folio.support.base.BaseSharedTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opensearch.index.query.QueryBuilders;

public abstract class IndexingLocationsConsortiumIT extends BaseSharedTest {

  @AfterEach
  void tearDown() throws IOException {
    deleteAllDocuments(LOCATION, CENTRAL_TENANT_ID);
    deleteAllDocuments(CAMPUS, CENTRAL_TENANT_ID);
    deleteAllDocuments(INSTITUTION, CENTRAL_TENANT_ID);
    deleteAllDocuments(LIBRARY, CENTRAL_TENANT_ID);
  }

  @MethodSource("testData")
  @ParameterizedTest
  void shouldIndexAndRemoveLocation(ResourceType resourceType, BaseLocationDto dto,
                                    UnaryOperator<String> topicNameFunction) {
    var createEvent = resourceEvent(CENTRAL_TENANT_ID, CREATE, toMap(dto), null);
    kafkaTemplate.send(topicNameFunction.apply(CENTRAL_TENANT_ID), createEvent);
    awaitIndexedResourceCounts(resourceType, CENTRAL_TENANT_ID, 1);

    var deleteEvent = resourceEvent(CENTRAL_TENANT_ID, DELETE, null, toMap(dto));
    kafkaTemplate.send(topicNameFunction.apply(CENTRAL_TENANT_ID), deleteEvent);
    awaitIndexedResourceCounts(resourceType, CENTRAL_TENANT_ID, 0);
  }

  @MethodSource("testData")
  @ParameterizedTest
  void shouldIndexSameLocationFromDifferentTenantsAsSeparateDocs(ResourceType resourceType, BaseLocationDto dto,
                                                                 UnaryOperator<String> topicNameFunction) {
    var createCentralEvent = resourceEvent(CENTRAL_TENANT_ID, CREATE, toMap(dto), null);
    var createMemberEvent = resourceEvent(MEMBER_TENANT_ID, CREATE, toMap(dto), null);
    kafkaTemplate.send(topicNameFunction.apply(CENTRAL_TENANT_ID), createCentralEvent);
    kafkaTemplate.send(topicNameFunction.apply(MEMBER_TENANT_ID), createMemberEvent);
    awaitIndexedResourceCounts(resourceType, CENTRAL_TENANT_ID, 2);
  }

  @MethodSource("testData")
  @ParameterizedTest
  void shouldRemoveAllLocationsByTenantIdOnDeleteAllEvent(ResourceType resourceType, BaseLocationDto dto,
                                                          UnaryOperator<String> topicNameFunction) {
    var createCentralEvent = resourceEvent(CENTRAL_TENANT_ID, CREATE, toMap(dto), null);
    var createMemberEvent = resourceEvent(MEMBER_TENANT_ID, CREATE, toMap(dto), null);
    kafkaTemplate.send(topicNameFunction.apply(CENTRAL_TENANT_ID), createCentralEvent);
    kafkaTemplate.send(topicNameFunction.apply(MEMBER_TENANT_ID), createMemberEvent);
    awaitIndexedResourceCounts(resourceType, CENTRAL_TENANT_ID, 2);

    var deleteAllMemberEvent = new ResourceEvent().type(DELETE_ALL).tenant(MEMBER_TENANT_ID);
    kafkaTemplate.send(topicNameFunction.apply(MEMBER_TENANT_ID), deleteAllMemberEvent);
    awaitIndexedResourceCounts(resourceType, CENTRAL_TENANT_ID, 1);
  }

  @MethodSource("testData")
  @ParameterizedTest
  void shouldIgnoreShadowLocation(ResourceType resourceType, BaseLocationDto dto,
                                  UnaryOperator<String> topicNameFunction) {
    var shadowMap = toMap(dto);
    shadowMap.put("isShadow", true);
    var sentinel = dto.toBuilder().id(randomId()).build();

    // the shadow and the sentinel event share the message key, so they land in the same partition and the shadow
    // event is consumed before (or in the same batch as) the sentinel event
    var topic = topicNameFunction.apply(MEMBER_TENANT_ID);
    var messageKey = randomId();
    kafkaTemplate.send(topic, messageKey, resourceEvent(CENTRAL_TENANT_ID, CREATE, shadowMap, null));
    kafkaTemplate.send(topic, messageKey, resourceEvent(CENTRAL_TENANT_ID, CREATE, toMap(sentinel), null));

    awaitIndexedResourceCounts(matchQuery("id", sentinel.getId()), resourceType, CENTRAL_TENANT_ID, 1);
    awaitIndexedResourceCounts(resourceType, CENTRAL_TENANT_ID, 1);
  }

  @MethodSource("testData")
  @ParameterizedTest
  void shouldIndexAndUpdateLocation(ResourceType resourceType, BaseLocationDto dto,
                                    UnaryOperator<String> topicNameFunction) {
    var createEvent = resourceEvent(CENTRAL_TENANT_ID, CREATE, toMap(dto), null);
    kafkaTemplate.send(topicNameFunction.apply(CENTRAL_TENANT_ID), createEvent);

    awaitIndexedResourceCounts(resourceType, CENTRAL_TENANT_ID, 1);

    var dtoUpdated = dto.toBuilder().name("nameUpdated").build();
    var updateEvent = resourceEvent(CENTRAL_TENANT_ID, UPDATE, toMap(dtoUpdated), toMap(dto));
    kafkaTemplate.send(topicNameFunction.apply(CENTRAL_TENANT_ID), updateEvent);

    awaitResourceUpdated(resourceType, dtoUpdated);
  }

  private static Stream<Arguments> testData() {
    return Stream.of(
      argumentSet("location", LOCATION, location(), (UnaryOperator<String>) TestConstants::inventoryLocationTopic),
      argumentSet("campus", CAMPUS, campus(), (UnaryOperator<String>) TestConstants::inventoryCampusTopic),
      argumentSet("institution", INSTITUTION, institution(),
        (UnaryOperator<String>) TestConstants::inventoryInstitutionTopic),
      argumentSet("library", LIBRARY, library(), (UnaryOperator<String>) TestConstants::inventoryLibraryTopic)
    );
  }

  private static void awaitResourceUpdated(ResourceType resourceType, BaseLocationDto dtoUpdated) {
    var idQuery = QueryBuilders.matchQuery("id", dtoUpdated.getId());
    var nameQuery = QueryBuilders.matchQuery("name", dtoUpdated.getName());
    var query = boolQuery().must(idQuery).must(nameQuery);
    awaitIndexedResourceCounts(query, resourceType, CENTRAL_TENANT_ID, 1);
  }

  private static InstitutionDto institution() {
    return InstitutionDto.builder().id(randomId())
      .name("name")
      .code("code")
      .metadata(metadata())
      .build();
  }

  private static CampusDto campus() {
    return CampusDto.builder().id(randomId())
      .name("name")
      .code("code")
      .institutionId(randomId())
      .metadata(metadata())
      .build();
  }

  private static LibraryDto library() {
    return LibraryDto.builder().id(randomId())
      .name("name")
      .code("code")
      .campusId(randomId())
      .metadata(metadata())
      .build();
  }

  private static LocationDto location() {
    var id = randomId();
    return LocationDto.builder().id(id)
      .name("location name")
      .code("CODE")
      .campusId(id)
      .institutionId(id)
      .description("desc")
      .discoveryDisplayName("display name")
      .primaryServicePoint(UUID.fromString(id))
      .isActive(true)
      .servicePointIds(List.of(UUID.fromString(id)))
      .metadata(metadata())
      .build();
  }

  private static Metadata metadata() {
    return new Metadata()
      .createdDate("2021-03-01T00:00:00.000+00:00")
      .updatedDate("2021-03-01T00:00:00.000+00:00");
  }
}
