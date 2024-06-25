package org.folio.search.service.locationunit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.SearchResponseHelper.getErrorIndexOperationResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.search.utils.SearchUtils.CAMPUS_RESOURCE;
import static org.folio.search.utils.SearchUtils.ID_FIELD;
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
import org.folio.search.client.CampusesClient;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.dto.locationunit.CampusDto;
import org.folio.search.service.ResourceService;
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
class CampusServiceTest {

  private @Mock ReindexConfigurationProperties properties;
  private @Mock CampusesClient client;
  private @Mock ResourceService resourceService;
  @InjectMocks
  private CampusService service;

  @Test
  void reindex_positive() {
    var batchSize = 3;
    var campusesMockPart1 = campuses(3);
    var campusesMockPart2 = campuses(2);
    var campusesMock = Stream.concat(campusesMockPart1.stream(), campusesMockPart2.stream()).toList();

    when(properties.getCampusBatchSize())
      .thenReturn(batchSize);
    when(client.getCampuses(0, batchSize))
      .thenReturn(campusResult(campusesMockPart1, campusesMock.size()));
    when(client.getCampuses(batchSize, batchSize))
      .thenReturn(campusResult(campusesMockPart2, campusesMock.size()));
    when(resourceService.indexResources(any()))
      .thenReturn(getSuccessIndexOperationResponse());

    service.reindex(TENANT_ID);

    log.info("reindex_positive:\n[\n\tbatchSize: {}\n\tcampusesMock: {}\n]",
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

    var expectedResourceIds = campusesMock.stream()
      .map(campus -> campus.get(ID_FIELD) + "|" + TENANT_ID)
      .toList();
    assertThat(captured)
      .extracting(ResourceEvent::getId)
      .containsExactlyElementsOf(expectedResourceIds);
  }

  @Test
  void reindex_negative_indexingError() {
    var batchSize = 2;
    var campusesMock = campuses(2);
    var error = "error";

    when(properties.getCampusBatchSize())
      .thenReturn(batchSize);
    when(client.getCampuses(0, batchSize))
      .thenReturn(campusResult(campusesMock, campusesMock.size()));
    when(resourceService.indexResources(any()))
      .thenReturn(getErrorIndexOperationResponse(error));

    var ex = assertThrows(IllegalStateException.class, () -> service.reindex(TENANT_ID));

    log.info("reindex_negative_indexingError:\n[\n\tbatchSize: {}\n\tcampusesMock: {}\n\terror: {}\n]",
      batchSize, campusesMock, ex.getMessage());

    assertThat(ex.getMessage()).isEqualTo(String.format("Indexing failed: %s", error));
  }

  private List<Map<String, Object>> campuses(int count) {
    return Stream.iterate(0, i -> i < count, i -> ++i)
      .map(i ->
        CampusDto.builder().id(randomId())
        .name("name" + i)
        .code("code" + i)
        .build())
      .map(TestUtils::toMap)
      .toList();
  }

  private CampusesClient.CampusesResult campusResult(List<Map<String, Object>> campuses, int totalCount) {
    return new CampusesClient.CampusesResult(campuses, totalCount);
  }
}
