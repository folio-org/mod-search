package org.folio.search.service.indexrebuild.derived;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.exception.ReindexException;
import org.folio.search.repository.PrimaryResourceRepository;
import org.folio.search.service.converter.MultiTenantSearchDocumentConverter;
import org.folio.search.service.indexrebuild.callnumber.CallNumberRebuildStrategy;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class DerivedDocumentWriterTest {

  private static final DerivedRebuildStrategy STRATEGY = new CallNumberRebuildStrategy(null, null);

  @Mock
  private DerivedIndexReader reader;
  @Mock
  private MultiTenantSearchDocumentConverter documentConverter;
  @Mock
  private PrimaryResourceRepository resourceRepository;
  @Captor
  private ArgumentCaptor<Collection<ResourceEvent>> eventsCaptor;
  private DerivedDocumentWriter writer;

  @BeforeEach
  void setUp() {
    writer = new DerivedDocumentWriter(reader, documentConverter, resourceRepository);
  }

  @Test
  void rebuild_positive_writesExistingAndDeletesKeysWithoutSourceDocuments() {
    var item = new HashMap<String, Object>();
    item.put("callNumberId", "alive");
    item.put("callNumber", "A1");
    item.put("instanceId", "i1");
    when(reader.findSourceDocuments(STRATEGY, TENANT_ID, List.of("alive", "gone"))).thenReturn(List.of(item));
    when(documentConverter.convertForReindex(eventsCaptor.capture())).thenReturn(List.of());
    when(resourceRepository.indexResources(any())).thenReturn(response(FolioIndexOperationResponse.StatusEnum.SUCCESS));

    writer.rebuild(STRATEGY, TENANT_ID, List.of("alive", "gone"));

    assertThat(eventsCaptor.getValue())
      .extracting(ResourceEvent::getId, ResourceEvent::getType, ResourceEvent::getResourceName)
      .containsExactly(tuple("alive", ResourceEventType.REINDEX, "instance_call_number"),
        tuple("gone", ResourceEventType.DELETE, "instance_call_number"));
  }

  @Test
  void rebuild_positive_sourceDocumentWithSeveralKeysBelongsToEachRequestedKey() {
    var strategy = new CallNumberRebuildStrategy(null, null) {
      @Override
      public Optional<Map<String, Object>> build(List<Map<String, Object>> sourceDocuments) {
        return Optional.of(Map.of("count", sourceDocuments.size()));
      }
    };
    var source = Map.<String, Object>of("callNumberId", List.of("a", "b", "other"));
    when(reader.findSourceDocuments(strategy, TENANT_ID, List.of("a", "b"))).thenReturn(List.of(source));
    when(documentConverter.convertForReindex(eventsCaptor.capture())).thenReturn(List.of());
    when(resourceRepository.indexResources(any())).thenReturn(response(FolioIndexOperationResponse.StatusEnum.SUCCESS));

    writer.rebuild(strategy, TENANT_ID, List.of("a", "b"));

    assertThat(eventsCaptor.getValue()).extracting(ResourceEvent::getId, ResourceEvent::getType)
      .containsExactly(tuple("a", ResourceEventType.REINDEX), tuple("b", ResourceEventType.REINDEX));
  }

  @Test
  void rebuild_negative_writeFailureIsThrown() {
    var keys = List.of("key");
    when(reader.findSourceDocuments(STRATEGY, TENANT_ID, keys)).thenReturn(List.of());
    when(documentConverter.convertForReindex(any())).thenReturn(List.of());
    when(resourceRepository.indexResources(any())).thenReturn(response(FolioIndexOperationResponse.StatusEnum.ERROR));

    assertThatThrownBy(() -> writer.rebuild(STRATEGY, TENANT_ID, keys)).isInstanceOf(ReindexException.class);
  }

  private static FolioIndexOperationResponse response(FolioIndexOperationResponse.StatusEnum status) {
    return new FolioIndexOperationResponse().status(status);
  }
}
