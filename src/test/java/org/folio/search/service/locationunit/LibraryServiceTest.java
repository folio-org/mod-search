package org.folio.search.service.locationunit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.SearchResponseHelper.getErrorIndexOperationResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.search.utils.SearchUtils.ID_FIELD;
import static org.folio.search.utils.SearchUtils.LIBRARY_RESOURCE;
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
import org.folio.search.client.LibrariesClient;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.dto.locationunit.LibraryDto;
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
class LibraryServiceTest {

  private @Mock ReindexConfigurationProperties properties;
  private @Mock LibrariesClient client;
  private @Mock ResourceService resourceService;
  @InjectMocks
  private LibraryService service;

  @Test
  void reindex_positive() {
    var batchSize = 3;
    var librariesMockPart1 = libraries(3);
    var librariesMockPart2 = libraries(2);
    var librariesMock = Stream.concat(librariesMockPart1.stream(), librariesMockPart2.stream()).toList();

    when(properties.getLibraryBatchSize())
      .thenReturn(batchSize);
    when(client.getLibraries(0, batchSize))
      .thenReturn(libraryResult(librariesMockPart1, librariesMock.size()));
    when(client.getLibraries(batchSize, batchSize))
      .thenReturn(libraryResult(librariesMockPart2, librariesMock.size()));
    when(resourceService.indexResources(any()))
      .thenReturn(getSuccessIndexOperationResponse());

    service.reindex(TENANT_ID);

    log.info("reindex_positive:\n[\n\tbatchSize: {}\n\tlibrariesMock: {}\n]",
      batchSize, librariesMock);

    var captor = ArgumentCaptor.<List<ResourceEvent>>captor();
    verify(resourceService, times(2)).indexResources(captor.capture());

    var captured = captor.getAllValues().stream()
      .flatMap(Collection::stream)
      .toList();
    assertThat(captured)
      .hasSize(librariesMock.size())
      .allMatch(resourceEvent -> resourceEvent.getTenant().equals(TENANT_ID))
      .allMatch(resourceEvent -> resourceEvent.getResourceName().equals(LIBRARY_RESOURCE))
      .extracting(ResourceEvent::getNew)
      .containsAll(librariesMock);

    var expectedResourceIds = librariesMock.stream()
      .map(library -> library.get(ID_FIELD) + "|" + TENANT_ID)
      .toList();
    assertThat(captured)
      .extracting(ResourceEvent::getId)
      .containsExactlyElementsOf(expectedResourceIds);
  }

  @Test
  void reindex_negative_indexingError() {
    var batchSize = 2;
    var librariesMock = libraries(2);
    var error = "error";

    when(properties.getLibraryBatchSize())
      .thenReturn(batchSize);
    when(client.getLibraries(0, batchSize))
      .thenReturn(libraryResult(librariesMock, librariesMock.size()));
    when(resourceService.indexResources(any()))
      .thenReturn(getErrorIndexOperationResponse(error));

    var ex = assertThrows(IllegalStateException.class, () -> service.reindex(TENANT_ID));

    log.info("reindex_negative_indexingError:\n[\n\tbatchSize: {}\n\tlibrariesMock: {}\n\terror: {}\n]",
      batchSize, librariesMock, ex.getMessage());

    assertThat(ex.getMessage()).isEqualTo(String.format("Indexing failed: %s", error));
  }

  private List<Map<String, Object>> libraries(int count) {
    return Stream.iterate(0, i -> i < count, i -> ++i)
      .map(i ->
        LibraryDto.builder().id(randomId())
        .name("name" + i)
        .code("code" + i)
        .build())
      .map(TestUtils::toMap)
      .toList();
  }

  private LibrariesClient.LibrariesResult libraryResult(List<Map<String, Object>> libraries, int totalCount) {
    return new LibrariesClient.LibrariesResult(libraries, totalCount);
  }
}
