package org.folio.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.SearchResponseHelper.getErrorIndexOperationResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
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
import org.folio.search.client.LocationsClient;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.dto.LocationDto;
import org.folio.search.utils.TestUtils;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

  private @Mock ReindexConfigurationProperties properties;
  private @Mock LocationsClient client;
  private @Mock ResourceService resourceService;
  @InjectMocks
  private LocationService service;

  @Test
  void reindex_positive() {
    var batchSize = 3;
    var locationsMockPart1 = locations(3);
    var locationsMockPart2 = locations(2);
    var locationsMock = Stream.concat(locationsMockPart1.stream(), locationsMockPart2.stream()).toList();

    when(properties.getLocationBatchSize()).thenReturn(batchSize);
    when(client.getLocations(0, batchSize)).thenReturn(locationResult(locationsMockPart1, locationsMock.size()));
    when(client.getLocations(batchSize, batchSize))
      .thenReturn(locationResult(locationsMockPart2, locationsMock.size()));
    when(resourceService.indexResources(any())).thenReturn(getSuccessIndexOperationResponse());

    service.reindex(TENANT_ID);

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

    var expectedResourceIds = locationsMock.stream()
      .map(location -> location.get(ID_FIELD) + "|" + TENANT_ID)
      .toList();
    assertThat(captured)
      .extracting(ResourceEvent::getId)
      .containsExactlyElementsOf(expectedResourceIds);
  }

  @Test
  void reindex_negative_indexingError() {
    var batchSize = 2;
    var locationsMock = locations(2);
    var error = "error";

    when(properties.getLocationBatchSize()).thenReturn(batchSize);
    when(client.getLocations(0, batchSize)).thenReturn(locationResult(locationsMock, locationsMock.size()));
    when(resourceService.indexResources(any())).thenReturn(getErrorIndexOperationResponse(error));

    var ex = assertThrows(IllegalStateException.class, () -> service.reindex(TENANT_ID));

    assertThat(ex.getMessage()).isEqualTo("Indexing failed: " + error);
  }

  private List<Map<String, Object>> locations(int count) {
    return Stream.iterate(0, i -> i < count, i -> ++i)
      .map(i ->
        LocationDto.builder().id(randomId())
        .name("name" + i)
        .code("CODE" + i)
        .build())
      .map(TestUtils::toMap)
      .toList();
  }

  private LocationsClient.LocationsResult locationResult(List<Map<String, Object>> locations, int totalCount) {
    return new LocationsClient.LocationsResult(locations, totalCount);
  }
}
