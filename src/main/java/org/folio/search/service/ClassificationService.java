package org.folio.search.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.ReindexClassificationsRequest;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.integration.KafkaMessageProducer;
import org.folio.search.model.event.ClassificationChunkEvent;
import org.folio.search.model.index.ClassificationResource;
import org.folio.search.model.index.InstanceSubResource;
import org.folio.search.repository.classification.InstanceClassificationEntityAgg;
import org.folio.search.repository.classification.InstanceClassificationRepository;
import org.folio.search.service.converter.MultiTenantSearchDocumentConverter;
import org.folio.search.utils.JsonConverter;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

import static org.folio.search.utils.SearchUtils.INSTANCE_CLASSIFICATION_RESOURCE;

@Log4j2
@Service
@RequiredArgsConstructor
public class ClassificationService {

  private final InstanceClassificationRepository instanceClassificationRepository;
  private final KafkaMessageProducer kafkaMessageProducer;
  private final JsonConverter jsonConverter;
  private final MultiTenantSearchDocumentConverter multiTenantSearchDocumentConverter;
  private final ResourceService resourceService;

  public void reindex(String tenantId, ReindexClassificationsRequest request) {
    int count = instanceClassificationRepository.countDistinctClassifications();
    if (count > 0) {
      int chunkSize = count / request.getParallelism();
      for (int i = 0; i < count; i += chunkSize) {
        kafkaMessageProducer.sendClassificationChunkEvent(tenantId, new ClassificationChunkEvent(
          tenantId,
          UUID.randomUUID(),
          i, chunkSize
        ));
      }
    }
  }

  public void processChunk(ResourceEvent value) {
    var chunkEvent = jsonConverter.convert(value.getNew(), ClassificationChunkEvent.class);
    var list = instanceClassificationRepository.fetchAggregatedChunk(chunkEvent.limit(), chunkEvent.offset())
      .stream()
      .map(ic -> toResourceCreateEvent(ic, chunkEvent.tenantId()))
      .toList();
    var converted = multiTenantSearchDocumentConverter.convert(list);
    resourceService.indexSearchDocuments(converted);
  }

  private ResourceEvent getResourceEvent(String tenant, String number, String typeId,
                                         Set<InstanceSubResource> instances, ResourceEventType eventType) {
    var id = StringUtils.deleteWhitespace(number + "|" + typeId);
    var resource = new ClassificationResource(id, typeId, number, instances);
    return new ResourceEvent()
      .id(id)
      .tenant(tenant)
      .resourceName(INSTANCE_CLASSIFICATION_RESOURCE)
      .type(eventType)
      ._new(jsonConverter.convertToMap(resource));
  }

  private ResourceEvent toResourceCreateEvent(InstanceClassificationEntityAgg source, String tenant) {
    return getResourceEvent(tenant, source.number(), source.typeId(), source.instances(), ResourceEventType.CREATE);
  }
}
