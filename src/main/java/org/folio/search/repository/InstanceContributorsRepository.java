package org.folio.search.repository;

import static java.util.stream.Collectors.groupingBy;
import static org.folio.search.utils.CollectionUtils.subtract;
import static org.folio.search.utils.CollectionUtils.subtractSorted;
import static org.folio.search.utils.SearchConverterUtils.getEventPayload;
import static org.folio.search.utils.SearchResponseHelper.getErrorIndexOperationResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.search.utils.SearchUtils.INSTANCE_CONTRIBUTORS_UPSERT_SCRIPT_ID;
import static org.opensearch.script.ScriptType.STORED;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.model.index.SearchDocumentBody;
import org.folio.search.model.types.IndexActionType;
import org.folio.search.utils.SearchUtils;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.script.Script;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class InstanceContributorsRepository extends AbstractResourceRepository {

  private final SearchConfigurationProperties properties;

  @Override
  public FolioIndexOperationResponse indexResources(List<SearchDocumentBody> esDocumentBodies) {
    var byId = esDocumentBodies.stream().collect(groupingBy(SearchDocumentBody::getId));
    var bulkRequest = new BulkRequest();
    for (var entry : byId.entrySet()) {
      var documents = entry.getValue();
      var instanceIdsToCreate = new HashSet<String>();
      var instanceIdsToDelete = new HashSet<String>();
      var typeIdsToCreate = new HashSet<String>();
      var typeIdsToDelete = new HashSet<String>();
      for (var document : documents) {
        var eventPayload = getPayload(document);
        var action = document.getAction();
        var instanceId = eventPayload.get("instanceId");
        var typeId = eventPayload.get("typeId");
        var pair = instanceId + "|" + typeId;
        if (action == IndexActionType.INDEX) {
          instanceIdsToCreate.add(pair);
          typeIdsToCreate.add(String.valueOf(typeId));
        } else {
          instanceIdsToDelete.add(pair);
          typeIdsToDelete.add(String.valueOf(typeId));
        }
      }

      var searchDocument = documents.iterator().next();
      var upsertRequest = new UpdateRequest()
        .id(searchDocument.getId())
        .scriptedUpsert(true)
        .retryOnConflict(properties.getIndexing().getInstanceContributors().getRetryAttempts())
        .index(SearchUtils.getIndexName(SearchUtils.CONTRIBUTOR_RESOURCE, searchDocument.getTenant()))
        .script(prepareScript(instanceIdsToCreate, instanceIdsToDelete))
        .upsert(prepareDocumentBody(getPayload(searchDocument), subtract(instanceIdsToCreate, instanceIdsToDelete),
          subtractSorted(typeIdsToCreate, typeIdsToDelete)), searchDocument.getDataFormat().getXcontentType());

      bulkRequest.add(upsertRequest);
    }

    var bulkApiResponse = executeBulkRequest(bulkRequest);

    return bulkApiResponse.hasFailures()
           ? getErrorIndexOperationResponse(bulkApiResponse.buildFailureMessage())
           : getSuccessIndexOperationResponse();
  }

  private Script prepareScript(HashSet<String> instanceIdsToCreate, HashSet<String> instanceIdsToDelete) {
    return new Script(STORED, null, INSTANCE_CONTRIBUTORS_UPSERT_SCRIPT_ID,
      Map.of("ins", instanceIdsToCreate, "del", instanceIdsToDelete));
  }

  private Map<String, Object> prepareDocumentBody(Map<String, Object> payload, Set<String> instanceIds,
                                                  Set<String> typeIds) {
    payload.put("contributorNameTypeId", payload.remove("nameTypeId"));
    payload.put("contributorTypeId", typeIds);
    payload.put("instances", instanceIds);
    payload.remove("instanceId");
    return payload;
  }

  private Map<String, Object> getPayload(SearchDocumentBody doc) {
    return getEventPayload(doc.getResourceEvent());
  }
}
