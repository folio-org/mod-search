package org.folio.search.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.model.event.ContributorResourceEvent;
import org.folio.search.model.index.ContributorResource;
import org.folio.search.model.index.SearchDocumentBody;
import org.folio.search.model.types.IndexActionType;
import org.folio.search.utils.JsonConverter;
import org.folio.search.utils.SearchUtils;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.common.bytes.BytesReference;
import org.opensearch.script.Script;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static java.util.stream.Collectors.groupingBy;
import static org.folio.search.utils.CollectionUtils.subtract;
import static org.folio.search.utils.CollectionUtils.subtractSorted;
import static org.folio.search.utils.CommonUtils.listToLogParamMsg;
import static org.folio.search.utils.SearchConverterUtils.getEventPayload;
import static org.folio.search.utils.SearchResponseHelper.getErrorIndexOperationResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.opensearch.script.Script.DEFAULT_SCRIPT_LANG;
import static org.opensearch.script.ScriptType.INLINE;

@Log4j2
@Repository
@RequiredArgsConstructor
public class InstanceContributorsRepository extends AbstractResourceRepository {

  public static final String SCRIPT = "def instanceIds=new LinkedHashSet(ctx._source.instances);"
    + "instanceIds.addAll(params.ins);"
    + "params.del.forEach(instanceIds::remove);"
    + "if (instanceIds.isEmpty()) {ctx.op = 'delete'; return;}"
    + "ctx._source.instances=instanceIds;"
    + "def typeIds=instanceIds.stream().map(id -> id.splitOnToken('|')[1])"
    + ".sorted().collect(Collectors.toCollection(LinkedHashSet::new));"
    + "ctx._source.contributorTypeId=typeIds";

  private final JsonConverter jsonConverter;
  private final Function<Map<String, Object>, BytesReference> searchDocumentBodyConverter;
  private final SearchConfigurationProperties properties;

  @Override
  public FolioIndexOperationResponse indexResources(List<SearchDocumentBody> esDocumentBodies) {
    log.debug("indexResources:: by [esDocumentBodies: {}]", listToLogParamMsg(esDocumentBodies));

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
        var instanceId = eventPayload.getInstanceId();
        var typeId = eventPayload.getTypeId();
        var pair = instanceId + "|" + typeId;
        if (action == IndexActionType.INDEX) {
          log.info("indexResources:: action == INDEX by [pair: {}]", pair);
          instanceIdsToCreate.add(pair);
          typeIdsToCreate.add(typeId);
        } else {
          log.info("indexResources:: action == DELETE by [pair: {}]", pair);
          instanceIdsToDelete.add(pair);
          typeIdsToDelete.add(typeId);
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

    if (bulkApiResponse.hasFailures()) {
      log.warn("BulkResponse has failure: {}", bulkApiResponse.buildFailureMessage());
      return getErrorIndexOperationResponse(bulkApiResponse.buildFailureMessage());
    }
    return getSuccessIndexOperationResponse();
  }

  private Script prepareScript(HashSet<String> instanceIdsToCreate, HashSet<String> instanceIdsToDelete) {
    return new Script(INLINE, DEFAULT_SCRIPT_LANG, SCRIPT,
      Map.of("ins", instanceIdsToCreate, "del", instanceIdsToDelete));
  }

  private byte[] prepareDocumentBody(ContributorResourceEvent payload, Set<String> instanceIds, Set<String> typeIds) {
    var resource = new ContributorResource();
    resource.setId(payload.getId());
    resource.setName(payload.getName());
    resource.setContributorTypeId(typeIds);
    resource.setContributorNameTypeId(payload.getNameTypeId());
    resource.setInstances(instanceIds);
    resource.setAuthorityId(payload.getAuthorityId());
    return BytesReference.toBytes(searchDocumentBodyConverter.apply(jsonConverter.convert(resource, Map.class)));
  }

  private ContributorResourceEvent getPayload(SearchDocumentBody doc) {
    return jsonConverter.fromJson(jsonConverter.toJson(getEventPayload(doc.getResourceEvent())),
      ContributorResourceEvent.class);
  }
}
