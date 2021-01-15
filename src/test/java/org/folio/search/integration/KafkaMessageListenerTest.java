package org.folio.search.integration;

import static org.folio.search.utils.SearchResponseUtils.getSuccessIndexOperationResponse;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.TestUtils.eventBody;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.randomId;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.folio.search.service.IndexService;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KafkaMessageListenerTest {

  @Mock private IndexService indexService;
  @InjectMocks private KafkaMessageListener messageListener;

  @Test
  void handleEvents() {
    var resourceEvents = List.of(eventBody(INSTANCE_RESOURCE, mapOf("id", randomId())));

    when(indexService.indexResources(resourceEvents)).thenReturn(getSuccessIndexOperationResponse());
    messageListener.handleEvents(resourceEvents);
    verify(indexService).indexResources(resourceEvents);
  }
}
