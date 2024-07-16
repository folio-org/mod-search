package org.folio.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.SearchResponseHelper.getErrorIndexOperationResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.search.utils.SearchUtils.CAMPUS_RESOURCE;
import static org.folio.search.utils.SearchUtils.ID_FIELD;
import static org.folio.search.utils.SearchUtils.INSTITUTION_RESOURCE;
import static org.folio.search.utils.SearchUtils.LIBRARY_RESOURCE;
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
import org.folio.search.model.dto.locationunit.InstitutionDto;
import org.folio.search.model.dto.locationunit.LibraryDto;
import org.folio.search.model.service.ResultList;
import org.folio.search.utils.TestUtils;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

  @ParameterizedTest
  @ValueSource(strings = {LOCATION_RESOURCE, CAMPUS_RESOURCE, LIBRARY_RESOURCE, INSTITUTION_RESOURCE})
  void reindex_locationsData_positive(String resourceName) {
    var batchSize = 3;
    var locationsDataMockPart1 = locationsData(resourceName, 3);
    var locationsDataMockPart2 = locationsData(resourceName, 2);
    var locationsDataMock = Stream.concat(locationsDataMockPart1.stream(), locationsDataMockPart2.stream()).toList();
    var uri = LocationsClient.DocumentType.valueOf(resourceName.toUpperCase()).getUri();

    when(properties.getLocationBatchSize())
        .thenReturn(batchSize);

    when(client.getLocationsData(uri, 0, batchSize))
      .thenReturn(documentsResult(locationsDataMockPart1, locationsDataMock.size()));
    when(client.getLocationsData(uri, batchSize, batchSize))
      .thenReturn(documentsResult(locationsDataMockPart2, locationsDataMock.size()));

    when(resourceService.indexResources(any()))
      .thenReturn(getSuccessIndexOperationResponse());

    service.reindex(TENANT_ID, resourceName);

    log.info("reindex_locationData_positive-{}:\n[\n\tbatchSize: {}\n\tmock: {}\n]",
      resourceName, batchSize, locationsDataMock);

    var captor = ArgumentCaptor.<List<ResourceEvent>>captor();
    verify(resourceService, times(2)).indexResources(captor.capture());

    var captured = captor.getAllValues().stream()
      .flatMap(Collection::stream)
      .toList();

    assertThat(captured)
      .hasSize(locationsDataMock.size())
      .allMatch(resourceEvent -> resourceEvent.getTenant().equals(TENANT_ID))
      .allMatch(resourceEvent -> resourceEvent.getResourceName().equals(resourceName))
      .extracting(ResourceEvent::getNew)
      .containsAll(locationsDataMock);

    var expectedResourceIds = locationsDataMock.stream()
      .map(document -> document.get(ID_FIELD) + "|" + TENANT_ID)
      .toList();

    log.info("reindex_locationData_positive-{}:\n[\n\texpectedResourceIds: {}\n]",
      resourceName, expectedResourceIds);

    assertThat(captured)
      .extracting(ResourceEvent::getId)
      .containsExactlyElementsOf(expectedResourceIds);
  }

  @ParameterizedTest
  @ValueSource(strings = {LOCATION_RESOURCE, CAMPUS_RESOURCE, LIBRARY_RESOURCE, INSTITUTION_RESOURCE})
  void reindex_locationsData_negative_indexingError(String resourceName) {
    var batchSize = 2;
    var locationsDataMock = locationsData(resourceName, 2);
    var error = "error";

    var uri = LocationsClient.DocumentType.valueOf(resourceName.toUpperCase()).getUri();
    when(properties.getLocationBatchSize())
      .thenReturn(batchSize);
    when(client.getLocationsData(uri, 0, batchSize))
      .thenReturn(documentsResult(locationsDataMock, locationsDataMock.size()));
    when(resourceService.indexResources(any()))
      .thenReturn(getErrorIndexOperationResponse(error));

    var ex = assertThrows(IllegalStateException.class, () -> service.reindex(TENANT_ID, resourceName));

    log.info("reindex_locationData_negative_indexingError-{}:\n[\n\tbatchSize: {}\n\tmock: {}\n\terror: {}\n]",
      resourceName, batchSize, locationsDataMock, ex.getMessage());

    assertThat(ex.getMessage()).isEqualTo(String.format("Indexing failed: %s", error));
  }

  private List<Map<String, Object>> locationsData(String resourceName, int count) {
    return Stream.iterate(0, i -> i < count, i -> ++i)
      .map(i -> locationsDataDto(resourceName, i))
      .map(TestUtils::toMap)
      .toList();
  }

  private Object locationsDataDto(String resourceName, int i) {
    var name = String.format("%s name%d", resourceName, i);
    var code = String.format("CODE%d", i);

    return switch (resourceName) {
      case LOCATION_RESOURCE -> LocationDto.builder().id(randomId()).name(name).code(code).build();
      case CAMPUS_RESOURCE -> CampusDto.builder().id(randomId()).name(name).code(code).institutionId(randomId()).build();
      case LIBRARY_RESOURCE -> LibraryDto.builder().id(randomId()).name(name).code(code).campusId(randomId()).build();
      case INSTITUTION_RESOURCE -> InstitutionDto.builder().id(randomId()).name(name).code(code).build();
      default -> throw new IllegalStateException("Unsupported document type: " + resourceName);
    };
  }

  private ResultList<Map<String, Object>> documentsResult(List<Map<String, Object>> documents, int totalCount) {
    return ResultList.of(totalCount, documents);
  }
}
