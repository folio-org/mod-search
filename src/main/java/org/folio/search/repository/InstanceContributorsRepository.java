package org.folio.search.repository;

import static java.util.stream.Collectors.groupingBy;
import static org.folio.search.utils.CollectionUtils.subtract;
import static org.folio.search.utils.SearchConverterUtils.getEventPayload;
import static org.folio.search.utils.SearchResponseHelper.getErrorIndexOperationResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.search.utils.SearchUtils.INSTANCE_CONTRIBUTORS_UPSERT_SCRIPT_ID;
import static org.opensearch.script.ScriptType.STORED;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.model.index.SearchDocumentBody;
import org.folio.search.model.types.IndexActionType;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.script.Script;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class InstanceContributorsRepository extends AbstractResourceRepository {

  private static final String INSTANCE_ID = "instanceId";
  private static final String TYPE_ID = "typeId";

  private final SearchConfigurationProperties properties;

  @Override
  public FolioIndexOperationResponse indexResources(List<SearchDocumentBody> esDocumentBodies) {
    var byId = esDocumentBodies.stream().collect(groupingBy(SearchDocumentBody::getId));
    var bulkRequest = new BulkRequest();
    for (var entry : byId.entrySet()) {
      var documents = entry.getValue();
      var instancesToCreate = new HashSet<Map<String, Object>>();
      var instancesToDelete = new HashSet<Map<String, Object>>();
      for (var document : documents) {
        var tenantId = document.getTenant();
        var eventPayload = getPayload(document);
        var action = document.getAction();
        var instanceId = eventPayload.get(INSTANCE_ID);
        var typeId = eventPayload.get(TYPE_ID);
        var shared = eventPayload.getOrDefault("shared", false);
        if (action == IndexActionType.INDEX) {
          instancesToCreate.add(prepareInstance(instanceId, tenantId, typeId, shared));
        } else {
          instancesToDelete.add(prepareInstance(instanceId, tenantId, typeId, shared));
        }
      }

      var searchDocument = documents.iterator().next();
      var upsertRequest = new UpdateRequest()
        .id(searchDocument.getId())
        .scriptedUpsert(true)
        .retryOnConflict(properties.getIndexing().getInstanceContributors().getRetryAttempts())
        .index(indexNameProvider.getIndexName(searchDocument))
        .script(prepareScript(instancesToCreate, instancesToDelete))
        .upsert(prepareDocumentBody(getPayload(searchDocument), subtract(instancesToCreate, instancesToDelete)),
          searchDocument.getDataFormat().getXcontentType());

      bulkRequest.add(upsertRequest);
    }

    var bulkApiResponse = executeBulkRequest(bulkRequest);

    return bulkApiResponse.hasFailures()
           ? getErrorIndexOperationResponse(bulkApiResponse.buildFailureMessage())
           : getSuccessIndexOperationResponse();
  }

  private Script prepareScript(HashSet<Map<String, Object>> instancesToCreate,
                               HashSet<Map<String, Object>> instancesToDelete) {
    return new Script(STORED, null, INSTANCE_CONTRIBUTORS_UPSERT_SCRIPT_ID,
      Map.of("ins", instancesToCreate, "del", instancesToDelete));
  }

  private Map<String, Object> prepareInstance(Object instanceId, String tenantId, Object typeId, Object shared) {
    var instancePayload = new HashMap<String, Object>();
    instancePayload.put(INSTANCE_ID, instanceId);
    instancePayload.put(TYPE_ID, typeId);
    instancePayload.put("tenantId", tenantId);
    instancePayload.put("shared", shared);

    return instancePayload;
  }

  private Map<String, Object> prepareDocumentBody(Map<String, Object> payload, Set<Map<String, Object>> instances) {
    payload.put("contributorNameTypeId", payload.remove("nameTypeId"));
    payload.put("instances", instances);
    payload.remove(INSTANCE_ID);
    payload.remove(TYPE_ID);
    return payload;
  }

  private Map<String, Object> getPayload(SearchDocumentBody doc) {
    return getEventPayload(doc.getResourceEvent());
  }
}
