package org.folio.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.SearchResponseHelper.getErrorIndexOperationResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.search.utils.SearchUtils.ID_FIELD;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.utils.TestUtils.randomId;
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
import org.folio.search.model.dto.locationunit.InstitutionDto;
import org.folio.search.model.dto.locationunit.LibraryDto;
import org.folio.search.model.service.ResultList;
import org.folio.search.model.types.ResourceType;
import org.folio.spring.testing.type.UnitTest;
import org.folio.support.utils.JsonTestUtils;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
  private @InjectMocks LocationService service;

  @ParameterizedTest
  @EnumSource(value = ResourceType.class,
              names = {"LOCATION", "CAMPUS", "LIBRARY", "INSTITUTION"},
              mode = EnumSource.Mode.INCLUDE)
  void reindex_locationsData_positive(ResourceType resource) {
    var batchSize = 3;
    var locationsDataMockPart1 = locationsData(resource, 3);
    var locationsDataMockPart2 = locationsData(resource, 2);
    var locationsDataMock = Stream.concat(locationsDataMockPart1.stream(), locationsDataMockPart2.stream()).toList();
    var uri = LocationsClient.DocumentType.valueOf(resource.getName().toUpperCase()).getUri();

    when(properties.getLocationBatchSize()).thenReturn(batchSize);
    when(client.getLocationsData(uri, 0, batchSize))
      .thenReturn(documentsResult(locationsDataMockPart1, locationsDataMock.size()));
    when(client.getLocationsData(uri, batchSize, batchSize))
      .thenReturn(documentsResult(locationsDataMockPart2, locationsDataMock.size()));
    when(resourceService.indexResources(any())).thenReturn(getSuccessIndexOperationResponse());

    service.reindex(TENANT_ID, resource);

    log.info("reindex_locationData_positive-{}:\n[\n\tbatchSize: {}\n\tmock: {}\n]",
      resource, batchSize, locationsDataMock);

    var captor = ArgumentCaptor.<List<ResourceEvent>>captor();
    verify(resourceService, times(2)).indexResources(captor.capture());

    var captured = assertLocationEvents(resource, captor, locationsDataMock);
    assertResourceIds(resource, locationsDataMock, captured);
  }

  @ParameterizedTest
  @EnumSource(value = ResourceType.class,
              names = {"LOCATION", "CAMPUS", "LIBRARY", "INSTITUTION"},
              mode = EnumSource.Mode.INCLUDE)
  void reindex_locationsData_negative_indexingError(ResourceType resource) {
    var batchSize = 2;
    var locationsDataMock = locationsData(resource, 2);
    var error = "error";

    var uri = LocationsClient.DocumentType.valueOf(resource.getName().toUpperCase()).getUri();
    when(properties.getLocationBatchSize()).thenReturn(batchSize);
    when(client.getLocationsData(uri, 0, batchSize))
      .thenReturn(documentsResult(locationsDataMock, locationsDataMock.size()));
    when(resourceService.indexResources(any())).thenReturn(getErrorIndexOperationResponse(error));

    var ex = assertThrows(IllegalStateException.class, () -> service.reindex(TENANT_ID, resource));

    log.info("reindex_locationData_negative_indexingError-{}:\n[\n\tbatchSize: {}\n\tmock: {}\n\terror: {}\n]",
      resource, batchSize, locationsDataMock, ex.getMessage());

    assertThat(ex.getMessage()).isEqualTo(String.format("Indexing failed: %s", error));
  }

  private List<ResourceEvent> assertLocationEvents(ResourceType resource,
                                                   ArgumentCaptor<List<ResourceEvent>> captor,
                                                   List<Map<String, Object>> locationsDataMock) {
    var captured = captor.getAllValues().stream()
      .flatMap(Collection::stream)
      .toList();

    assertThat(captured)
      .hasSize(locationsDataMock.size())
      .allMatch(resourceEvent -> resourceEvent.getTenant().equals(TENANT_ID))
      .allMatch(resourceEvent -> resourceEvent.getResourceName().equals(resource.getName()))
      .extracting(ResourceEvent::getNew)
      .containsAll(locationsDataMock);
    return captured;
  }

  private void assertResourceIds(ResourceType resource, List<Map<String, Object>> locationsDataMock,
                                 List<ResourceEvent> captured) {
    var expectedResourceIds = locationsDataMock.stream()
      .map(document -> document.get(ID_FIELD) + "|" + TENANT_ID)
      .toList();

    log.info("reindex_locationData_positive-{}:\n[\n\texpectedResourceIds: {}\n]",
      resource, expectedResourceIds);

    assertThat(captured)
      .extracting(ResourceEvent::getId)
      .containsExactlyElementsOf(expectedResourceIds);
  }

  private List<Map<String, Object>> locationsData(ResourceType resourceName, int count) {
    return Stream.iterate(0, i -> i < count, i -> ++i)
      .map(i -> locationsDataDto(resourceName, i))
      .map(JsonTestUtils::toMap)
      .toList();
  }

  private Object locationsDataDto(ResourceType resourceType, int i) {
    var id = randomId();
    var name = String.format("%s name%d", resourceType.getName(), i);
    var code = String.format("CODE%d", i);

    return switch (resourceType) {
      case LOCATION -> LocationDto.builder().id(id).name(name).code(code).build();
      case CAMPUS -> CampusDto.builder().id(id).name(name).code(code).institutionId(randomId()).build();
      case LIBRARY -> LibraryDto.builder().id(id).name(name).code(code).campusId(randomId()).build();
      case INSTITUTION -> InstitutionDto.builder().id(id).name(name).code(code).build();
      default -> throw new IllegalStateException("Unsupported document type: " + resourceType);
    };
  }

  private ResultList<Map<String, Object>> documentsResult(List<Map<String, Object>> documents, int totalCount) {
    return ResultList.of(totalCount, documents);
  }
}
