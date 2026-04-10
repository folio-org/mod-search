package org.folio.search.service.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.model.index.InstanceSearchDocument;
import org.folio.search.model.index.InstanceSearchDocumentBody;
import org.folio.search.model.types.IndexActionType;
import org.folio.search.model.types.IndexingDataFormat;
import org.opensearch.common.xcontent.smile.SmileXContent;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.common.bytes.BytesReference;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class InstanceSearchDocumentConverter {

  private final ObjectMapper objectMapper;
  private final SearchConfigurationProperties searchConfigurationProperties;

  public InstanceSearchDocumentBody convert(InstanceSearchDocument document, String targetIndex) {
    var dataFormat = searchConfigurationProperties.getIndexing().getDataFormat();
    var routingKey = document.getInstanceId();
    var body = serializeDocument(document, dataFormat);

    var resourceEvent = new ResourceEvent()
      .id(document.getId())
      .tenant(document.getTenantId())
      .resourceName("instance_search");

    return InstanceSearchDocumentBody.of(
      body, dataFormat, resourceEvent, IndexActionType.INDEX,
      targetIndex, routingKey, document.getSourceVersion()
    );
  }

  public InstanceSearchDocumentBody convertForDelete(String id, String instanceId, String tenantId,
                                                     String targetIndex, long sourceVersion) {
    var resourceEvent = new ResourceEvent()
      .id(id)
      .tenant(tenantId)
      .resourceName("instance_search");

    return InstanceSearchDocumentBody.of(
      BytesArray.EMPTY, IndexingDataFormat.JSON, resourceEvent, IndexActionType.DELETE,
      targetIndex, instanceId, sourceVersion
    );
  }

  private BytesReference serializeDocument(InstanceSearchDocument document, IndexingDataFormat dataFormat) {
    try {
      var docMap = buildDocumentMap(document);
      if (dataFormat == IndexingDataFormat.SMILE) {
        try (var builder = SmileXContent.contentBuilder()) {
          builder.map(docMap);
          return BytesReference.bytes(builder);
        }
      } else {
        var bytes = objectMapper.writeValueAsBytes(docMap);
        return new BytesArray(bytes);
      }
    } catch (Exception e) {
      throw new SearchServiceException("Failed to serialize instance search document: " + document.getId(), e);
    }
  }

  private Map<String, Object> buildDocumentMap(InstanceSearchDocument document) {
    var docMap = new LinkedHashMap<String, Object>();
    docMap.put("id", document.getId());
    docMap.put("resourceType", document.getResourceType());
    docMap.put("instanceId", document.getInstanceId());
    docMap.put("tenantId", document.getTenantId());
    docMap.put("shared", document.isShared());
    docMap.put("join_field", document.getJoinField());

    if (document.getFields() != null) {
      // Namespace type-specific fields under their document type key
      docMap.put(document.getResourceType(), document.getFields());
    }

    return docMap;
  }
}
