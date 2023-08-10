package org.folio.search.repository;

import static java.util.stream.Collectors.groupingBy;
import static org.folio.search.model.types.IndexActionType.DELETE;
import static org.folio.search.model.types.IndexActionType.INDEX;
import static org.folio.search.utils.CollectionUtils.subtract;
import static org.folio.search.utils.SearchConverterUtils.getEventPayload;
import static org.folio.search.utils.SearchResponseHelper.getErrorIndexOperationResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.search.utils.SearchUtils.INSTANCE_SUBJECT_UPSERT_SCRIPT_ID;
import static org.opensearch.script.ScriptType.STORED;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.model.index.SearchDocumentBody;
import org.folio.search.model.types.IndexActionType;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.script.Script;
import org.springframework.stereotype.Repository;

@Log4j2
@Repository
@RequiredArgsConstructor
public class InstanceSubjectRepository extends AbstractResourceRepository {

  private final SearchConfigurationProperties properties;

  @Override
  public FolioIndexOperationResponse indexResources(List<SearchDocumentBody> documentBodies) {
    var bulkRequest = new BulkRequest();

    var docsById = documentBodies.stream().collect(groupingBy(SearchDocumentBody::getId));
    for (var entry : docsById.entrySet()) {
      var documents = entry.getValue();
      var instanceIds = prepareInstanceIds(documents);
      if (!IterableUtils.matchesAll(instanceIds.values(), Set::isEmpty)) {
        var upsertRequest = prepareUpsertRequest(documents.iterator().next(), instanceIds);
        bulkRequest.add(upsertRequest);
      }
    }

    var bulkApiResponse = executeBulkRequest(bulkRequest);

    return bulkApiResponse.hasFailures()
           ? getErrorIndexOperationResponse(bulkApiResponse.buildFailureMessage())
           : getSuccessIndexOperationResponse();
  }

  private EnumMap<IndexActionType, Set<String>> prepareInstanceIds(List<SearchDocumentBody> documents) {
    var instanceIds = prepareInstanceIdMap();
    for (var document : documents) {
      var payload = getPayload(document);
      var instanceId = String.valueOf(payload.get("instanceId"));
      if (StringUtils.isNotBlank(instanceId)) {
        instanceIds.getOrDefault(document.getAction(), instanceIds.get(DELETE)).add(instanceId);
      } else {
        log.warn("InstanceId is blank in subject event. [payload: {}]", payload);
      }
    }
    return instanceIds;
  }

  private EnumMap<IndexActionType, Set<String>> prepareInstanceIdMap() {
    var instanceIds = new EnumMap<IndexActionType, Set<String>>(IndexActionType.class);
    instanceIds.put(INDEX, new HashSet<>());
    instanceIds.put(DELETE, new HashSet<>());
    return instanceIds;
  }

  private UpdateRequest prepareUpsertRequest(SearchDocumentBody doc,
                                             EnumMap<IndexActionType, Set<String>> instanceIds) {
    return new UpdateRequest()
      .id(doc.getId())
      .scriptedUpsert(true)
      .retryOnConflict(properties.getIndexing().getInstanceSubjects().getRetryAttempts())
      .index(indexNameProvider.getIndexName(doc))
      .script(new Script(STORED, null, INSTANCE_SUBJECT_UPSERT_SCRIPT_ID, prepareScriptParams(instanceIds)))
      .upsert(prepareDocumentBody(getPayload(doc), instanceIds), doc.getDataFormat().getXcontentType());
  }

  private Map<String, Object> prepareScriptParams(EnumMap<IndexActionType, Set<String>> instanceIds) {
    return Map.of("ins", instanceIds.get(INDEX), "del", instanceIds.get(DELETE));
  }

  private Map<String, Object> prepareDocumentBody(Map<String, Object> payload,
                                                  Map<IndexActionType, Set<String>> instanceIds) {
    payload.put("instances", subtract(instanceIds.get(INDEX), instanceIds.get(DELETE)));
    payload.remove("instanceId");
    return payload;
  }

  private Map<String, Object> getPayload(SearchDocumentBody doc) {
    return getEventPayload(doc.getResourceEvent());
  }
}
