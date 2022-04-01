package org.folio.search.repository;

import static java.util.stream.Collectors.groupingBy;
import static org.elasticsearch.common.xcontent.XContentType.JSON;
import static org.elasticsearch.script.Script.DEFAULT_SCRIPT_LANG;
import static org.elasticsearch.script.ScriptType.INLINE;
import static org.folio.search.utils.CollectionUtils.subtract;
import static org.folio.search.utils.SearchConverterUtils.getEventPayload;
import static org.folio.search.utils.SearchResponseHelper.getErrorIndexOperationResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.script.Script;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.model.index.SearchDocumentBody;
import org.folio.search.model.types.IndexActionType;
import org.folio.search.utils.JsonConverter;
import org.folio.search.utils.SearchUtils;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class InstanceContributorsRepository extends AbstractResourceRepository {

  public static final String SCRIPT_1 = "def set=new LinkedHashSet(ctx._source.instances);"
    + "set.addAll(params.ins);params.del.forEach(set::remove);ctx._source.instances=set";

  private final JsonConverter jsonConverter;

  @Override
  public FolioIndexOperationResponse indexResources(List<SearchDocumentBody> esDocumentBodies) {
    var byId = esDocumentBodies.stream().collect(groupingBy(SearchDocumentBody::getId));
    var bulkRequest = new BulkRequest();
    for (var entry : byId.entrySet()) {
      var documents = entry.getValue();
      var idsToCreate = new ArrayList<String>();
      var idsToDelete = new ArrayList<String>();
      for (var document : documents) {
        var eventPayload = getEventPayload(document.getResourceEvent());
        var action = document.getAction();
        var instanceId = MapUtils.getString(eventPayload, "instanceId");
        var typeNameId = MapUtils.getString(eventPayload, "contributorNameTypeId", "null");
        var pair = instanceId + "|" + typeNameId;
        if (action == IndexActionType.INDEX) {
          idsToCreate.add(pair);
        } else {
          idsToDelete.add(pair);
        }
      }

      var searchDocument = documents.iterator().next();
      var upsertRequest = new UpdateRequest()
        .id(searchDocument.getId())
        .routing(searchDocument.getRouting())
        .index(SearchUtils.getIndexName(SearchUtils.CONTRIBUTOR_RESOURCE, searchDocument.getRouting()))
        .script(new Script(INLINE, DEFAULT_SCRIPT_LANG, SCRIPT_1, Map.of("ins", idsToCreate, "del", idsToDelete)))
        .upsert(getContributorJsonBody(searchDocument, idsToCreate, idsToDelete), JSON);

      bulkRequest.add(upsertRequest);
    }

    var bulkApiResponse = executeBulkRequest(bulkRequest);

    return bulkApiResponse.hasFailures()
      ? getErrorIndexOperationResponse(bulkApiResponse.buildFailureMessage())
      : getSuccessIndexOperationResponse();
  }

  private String getContributorJsonBody(SearchDocumentBody doc, List<String> idsToCreate, List<String> idsToDelete) {
    var payload = getEventPayload(doc.getResourceEvent());
    var resource = new LinkedHashMap<String, Object>();
    resource.put("id", doc.getId());
    resource.put("name", payload.get("name"));
    resource.put("type", payload.get("contributorTypeId"));
    resource.put("instances", subtract(idsToCreate, idsToDelete));
    return jsonConverter.toJson(resource);
  }
}
