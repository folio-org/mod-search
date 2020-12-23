package org.folio.search.integration;

import static org.folio.search.model.rest.response.FolioIndexResourceResponse.success;
import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;
import static org.folio.search.utils.TestUtils.randomId;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.folio.search.model.ResourceEventBody;
import org.folio.search.service.IndexService;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class EventListenerTest {

  @Mock private IndexService indexService;
  @InjectMocks private KafkaMessageListener messageListener;

  @Test
  void handleEvents() {
    var instanceData = OBJECT_MAPPER.createObjectNode();
    instanceData.put("id", randomId());
    var resourceBody = ResourceEventBody.of("CREATE", "tenant", "instance", instanceData);
    var resourceEvents = List.of(resourceBody);

    when(indexService.indexResources(resourceEvents)).thenReturn(success());
    messageListener.handleEvents(resourceEvents);
    verify(indexService).indexResources(resourceEvents);
  }
}
