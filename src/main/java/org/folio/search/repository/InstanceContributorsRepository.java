package org.folio.search.repository;

import static java.util.stream.Collectors.groupingBy;
import static org.opensearch.common.xcontent.XContentType.JSON;
import static org.opensearch.script.Script.DEFAULT_SCRIPT_LANG;
import static org.opensearch.script.ScriptType.INLINE;
import static org.folio.search.utils.CollectionUtils.subtract;
import static org.folio.search.utils.CollectionUtils.subtractSorted;
import static org.folio.search.utils.SearchConverterUtils.getEventPayload;
import static org.folio.search.utils.SearchResponseHelper.getErrorIndexOperationResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.script.Script;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.model.event.ContributorEvent;
import org.folio.search.model.index.ContributorResource;
import org.folio.search.model.index.SearchDocumentBody;
import org.folio.search.model.types.IndexActionType;
import org.folio.search.utils.JsonConverter;
import org.folio.search.utils.SearchUtils;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class InstanceContributorsRepository extends AbstractResourceRepository {

  public static final String SCRIPT_1 = "def instanceIds=new LinkedHashSet(ctx._source.instances);"
    + "instanceIds.addAll(params.ins);"
    + "params.del.forEach(instanceIds::remove);"
    + "if (instanceIds.isEmpty()) {ctx.op = 'delete'; return;}"
    + "ctx._source.instances=instanceIds;"
    + "def typeIds=instanceIds.stream().map(id -> id.splitOnToken('|')[1])"
    + ".sorted().collect(Collectors.toCollection(LinkedHashSet::new));"
    + "ctx._source.contributorTypeId=typeIds";

  private final JsonConverter jsonConverter;

  @Override
  public FolioIndexOperationResponse indexResources(List<SearchDocumentBody> esDocumentBodies) {
    var byId = esDocumentBodies.stream().collect(groupingBy(SearchDocumentBody::getId));
    var bulkRequest = new BulkRequest();
    for (var entry : byId.entrySet()) {
      var documents = entry.getValue();
      var instanceIdsToCreate = new HashSet<String>();
      var instanceIdsToDelete = new HashSet<String>();
      var nameIdsToCreate = new HashSet<String>();
      var nameIdsToDelete = new HashSet<String>();
      for (var document : documents) {
        var eventPayload = getPayload(document);
        var action = document.getAction();
        var instanceId = eventPayload.getInstanceId();
        var typeId = eventPayload.getTypeId();
        var pair = instanceId + "|" + typeId;
        if (action == IndexActionType.INDEX) {
          instanceIdsToCreate.add(pair);
          nameIdsToCreate.add(typeId);
        } else {
          instanceIdsToDelete.add(pair);
          nameIdsToDelete.add(typeId);
        }
      }

      var searchDocument = documents.iterator().next();
      var upsertRequest = new UpdateRequest()
        .id(searchDocument.getId())
        .routing(searchDocument.getRouting())
        .index(SearchUtils.getIndexName(SearchUtils.CONTRIBUTOR_RESOURCE, searchDocument.getRouting()))
        .script(new Script(INLINE, DEFAULT_SCRIPT_LANG, SCRIPT_1,
          Map.of("ins", instanceIdsToCreate, "del", instanceIdsToDelete)))
        .upsert(getContributorJsonBody(getPayload(searchDocument), subtract(instanceIdsToCreate, instanceIdsToDelete),
          subtractSorted(nameIdsToCreate, nameIdsToDelete)), JSON);

      bulkRequest.add(upsertRequest);
    }

    var bulkApiResponse = executeBulkRequest(bulkRequest);

    return bulkApiResponse.hasFailures()
           ? getErrorIndexOperationResponse(bulkApiResponse.buildFailureMessage())
           : getSuccessIndexOperationResponse();
  }

  private String getContributorJsonBody(ContributorEvent payload, Set<String> instanceIds, Set<String> nameTypeIds) {
    var resource = new ContributorResource();
    resource.setId(payload.getId());
    resource.setName(payload.getName());
    resource.setContributorTypeId(nameTypeIds);
    resource.setContributorNameTypeId(payload.getNameTypeId());
    resource.setInstances(instanceIds);
    return jsonConverter.toJson(resource);
  }

  private ContributorEvent getPayload(SearchDocumentBody doc) {
    return jsonConverter.fromJson(jsonConverter.toJson(getEventPayload(doc.getResourceEvent())),
      ContributorEvent.class);
  }
}
