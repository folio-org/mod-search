package org.folio.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.SearchResponseHelper.getErrorIndexOperationResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.search.utils.SearchUtils.CAMPUS_RESOURCE;
import static org.folio.search.utils.SearchUtils.ID_FIELD;
import static org.folio.search.utils.SearchUtils.LOCATION_RESOURCE;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.randomId;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.extern.log4j.Log4j2;
import org.folio.search.client.LocationsClient;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.dto.LocationDto;
import org.folio.search.model.dto.locationunit.CampusDto;
import org.folio.search.model.service.ResultList;
import org.folio.search.utils.TestUtils;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Log4j2
@UnitTest
@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

  private @Mock ReindexConfigurationProperties properties;
  private @Mock LocationsClient client;
  private @Mock ResourceService resourceService;
  @InjectMocks
  private LocationService service;

  @Test
  void reindex_locations_positive() {
    var batchSize = 3;
    var locationsMockPart1 = locations(3);
    var locationsMockPart2 = locations(2);
    var locationsMock = Stream.concat(locationsMockPart1.stream(), locationsMockPart2.stream()).toList();
    var localtionUri = LocationsClient.DocumentType.LOCATION.getUri();

    when(properties.getLocationBatchSize())
        .thenReturn(batchSize);

    when(client.getLocationsData(localtionUri, 0, batchSize))
      .thenReturn(documentsResult(locationsMockPart1, locationsMock.size()));
    when(client.getLocationsData(localtionUri, batchSize, batchSize))
      .thenReturn(documentsResult(locationsMockPart2, locationsMock.size()));

    when(resourceService.indexResources(any()))
      .thenReturn(getSuccessIndexOperationResponse());

    service.reindex(TENANT_ID, LOCATION_RESOURCE);

    log.info("reindex_locations_positive:\n[\n\tbatchSize: {}\n\tlocationsMock: {}\n]",
      batchSize, locationsMock);

    var captor = ArgumentCaptor.<List<ResourceEvent>>captor();
    verify(resourceService, times(2)).indexResources(captor.capture());

    var captured = captor.getAllValues().stream()
      .flatMap(Collection::stream)
      .toList();

    assertThat(captured)
      .hasSize(locationsMock.size())
      .allMatch(resourceEvent -> resourceEvent.getTenant().equals(TENANT_ID))
      .allMatch(resourceEvent -> resourceEvent.getResourceName().equals(LOCATION_RESOURCE))
      .extracting(ResourceEvent::getNew)
      .containsAll(locationsMock);

    var expectedLocationsResourceIds = locationsMock.stream()
      .map(location -> location.get(ID_FIELD) + "|" + TENANT_ID)
      .toList();

    log.info("reindex_locations_positive:\n[\n\texpectedLocationsResourceIds: {}\n]",
      expectedLocationsResourceIds);

    assertThat(captured)
      .extracting(ResourceEvent::getId)
      .containsExactlyElementsOf(expectedLocationsResourceIds);
  }

  @Test
  void reindex_campuses_positive() {
    var batchSize = 4;
    var campusesMockPart1 = campuses(4);
    var campusesMockPart2 = campuses(3);
    var campusesMock = Stream.concat(campusesMockPart1.stream(), campusesMockPart2.stream()).toList();
    var campusesUri = LocationsClient.DocumentType.CAMPUS.getUri();

    when(properties.getLocationBatchSize())
      .thenReturn(batchSize);

    when(client.getLocationsData(campusesUri, 0, batchSize))
      .thenReturn(documentsResult(campusesMockPart1, campusesMock.size()));
    when(client.getLocationsData(campusesUri, batchSize, batchSize))
      .thenReturn(documentsResult(campusesMockPart2, campusesMock.size()));

    when(resourceService.indexResources(any()))
      .thenReturn(getSuccessIndexOperationResponse());

    service.reindex(TENANT_ID, CAMPUS_RESOURCE);

    log.info("reindex_campuses_positive:\n[\n\tbatchSize: {}\n\tcampusesMock: {}\n]",
      batchSize, campusesMock);

    var captor = ArgumentCaptor.<List<ResourceEvent>>captor();
    verify(resourceService, times(2)).indexResources(captor.capture());

    var captured = captor.getAllValues().stream()
      .flatMap(Collection::stream)
      .toList();

    assertThat(captured)
      .hasSize(campusesMock.size())
      .allMatch(resourceEvent -> resourceEvent.getTenant().equals(TENANT_ID))
      .allMatch(resourceEvent -> resourceEvent.getResourceName().equals(CAMPUS_RESOURCE))
      .extracting(ResourceEvent::getNew)
      .containsAll(campusesMock);

    var expectedCampusResourceIds = campusesMock.stream()
      .map(campus -> campus.get(ID_FIELD) + "|" + TENANT_ID)
      .toList();

    log.info("reindex_campuses_positive:\n[\n\texpectedCampusResourceIds: {}\n]",
      expectedCampusResourceIds);

    assertThat(captured)
      .extracting(ResourceEvent::getId)
      .containsExactlyElementsOf(expectedCampusResourceIds);
  }

  @Test
  void reindex_locations_negative_indexingError() {
    var batchSize = 2;
    var locationsMock = locations(2);
    var error = "error";

    var localtionUri = LocationsClient.DocumentType.LOCATION.getUri();
    when(properties.getLocationBatchSize())
      .thenReturn(batchSize);
    when(client.getLocationsData(localtionUri, 0, batchSize))
      .thenReturn(documentsResult(locationsMock, locationsMock.size()));
    when(resourceService.indexResources(any()))
      .thenReturn(getErrorIndexOperationResponse(error));

    var ex = assertThrows(IllegalStateException.class, () -> service.reindex(TENANT_ID, LOCATION_RESOURCE));

    log.info("reindex_locations_negative_indexingError:\n[\n\tbatchSize: {}\n\tlocationsMock: {}\n\terror: {}\n]",
      batchSize, locationsMock, ex.getMessage());

    assertThat(ex.getMessage()).isEqualTo(String.format("Indexing failed: %s", error));
  }

  @Test
  void reindex_campuses_negative_indexingError() {
    var batchSize = 4;
    var campusesMock = campuses(4);
    var error = "error";

    var campusUri = LocationsClient.DocumentType.CAMPUS.getUri();
    when(properties.getLocationBatchSize())
      .thenReturn(batchSize);
    when(client.getLocationsData(campusUri, 0, batchSize))
      .thenReturn(documentsResult(campusesMock, campusesMock.size()));
    when(resourceService.indexResources(any()))
      .thenReturn(getErrorIndexOperationResponse(error));

    var ex = assertThrows(IllegalStateException.class, () -> service.reindex(TENANT_ID, CAMPUS_RESOURCE));

    log.info("reindex_campuses_negative_indexingError:\n[\n\tbatchSize: {}\n\tcampusesMock: {}\n\terror: {}\n]",
      batchSize, campusesMock, ex.getMessage());

    assertThat(ex.getMessage()).isEqualTo(String.format("Indexing failed: %s", error));
  }

  private List<Map<String, Object>> locations(int count) {
    return Stream.iterate(0, i -> i < count, i -> ++i)
      .map(i ->
        LocationDto.builder().id(randomId())
          .name("Location name" + i)
          .code("CODE" + i)
          .build())
      .map(TestUtils::toMap)
      .toList();
  }

  private List<Map<String, Object>> campuses(int count) {
    return Stream.iterate(0, i -> i < count, i -> ++i)
      .map(i ->
        CampusDto.builder().id(randomId())
          .name("Campus name" + i)
          .code("CODE" + i)
          .build())
      .map(TestUtils::toMap)
      .toList();
  }

  private ResultList<Map<String, Object>> documentsResult(List<Map<String, Object>> documents, int totalCount) {
    return ResultList.of(totalCount, documents);
  }
}
