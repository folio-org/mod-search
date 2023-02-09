package org.folio.search.repository;

import static java.util.stream.Collectors.groupingBy;
import static org.folio.search.model.types.IndexActionType.DELETE;
import static org.folio.search.model.types.IndexActionType.INDEX;
import static org.folio.search.utils.CollectionUtils.subtract;
import static org.folio.search.utils.CommonUtils.listToLogMsg;
import static org.folio.search.utils.SearchConverterUtils.getEventPayload;
import static org.folio.search.utils.SearchResponseHelper.getErrorIndexOperationResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.search.utils.SearchUtils.INSTANCE_SUBJECT_RESOURCE;
import static org.opensearch.script.Script.DEFAULT_SCRIPT_LANG;
import static org.opensearch.script.ScriptType.INLINE;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.model.event.SubjectResourceEvent;
import org.folio.search.model.index.SearchDocumentBody;
import org.folio.search.model.index.SubjectResource;
import org.folio.search.model.types.IndexActionType;
import org.folio.search.utils.JsonConverter;
import org.folio.search.utils.SearchUtils;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.common.bytes.BytesReference;
import org.opensearch.script.Script;
import org.springframework.stereotype.Repository;

@Log4j2
@Repository
@RequiredArgsConstructor
public class InstanceSubjectRepository extends AbstractResourceRepository {

  public static final String SCRIPT = "def instanceIds=new LinkedHashSet(ctx._source.instances);"
    + "instanceIds.addAll(params.ins);"
    + "params.del.forEach(instanceIds::remove);"
    + "if (instanceIds.isEmpty()) {ctx.op = 'delete'; return;}"
    + "ctx._source.instances=instanceIds;";

  private final SearchConfigurationProperties properties;
  private final JsonConverter jsonConverter;
  private final Function<Map<String, Object>, BytesReference> searchDocumentBodyConverter;

  @Override
  public FolioIndexOperationResponse indexResources(List<SearchDocumentBody> documentBodies) {
    log.debug("indexResources:: by [documentBodies: {}]", listToLogMsg(documentBodies, true));
    var bulkRequest = new BulkRequest();

    var docsById = documentBodies.stream().collect(groupingBy(SearchDocumentBody::getId));
    for (var entry : docsById.entrySet()) {
      var documents = entry.getValue();
      var upsertRequest = prepareUpsertRequest(documents.iterator().next(), prepareInstanceIds(documents));
      bulkRequest.add(upsertRequest);
    }

    var bulkApiResponse = executeBulkRequest(bulkRequest);

    if (bulkApiResponse.hasFailures()) {
      log.warn("BulkResponse has failure: {}", bulkApiResponse.buildFailureMessage());
      return getErrorIndexOperationResponse(bulkApiResponse.buildFailureMessage());
    }
    return getSuccessIndexOperationResponse();
  }

  private EnumMap<IndexActionType, Set<String>> prepareInstanceIds(List<SearchDocumentBody> documents) {
    var instanceIds = prepareInstanceIdMap();
    for (var document : documents) {
      var eventPayload = getPayload(document);
      var instanceId = eventPayload.getInstanceId();
      instanceIds.getOrDefault(document.getAction(), instanceIds.get(DELETE)).add(instanceId);
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
      .index(SearchUtils.getIndexName(INSTANCE_SUBJECT_RESOURCE, doc.getTenant()))
      .script(new Script(INLINE, DEFAULT_SCRIPT_LANG, SCRIPT, prepareScriptParams(instanceIds)))
      .upsert(prepareDocumentBody(getPayload(doc), instanceIds), doc.getDataFormat().getXcontentType());
  }

  private Map<String, Object> prepareScriptParams(EnumMap<IndexActionType, Set<String>> instanceIds) {
    return Map.of("ins", instanceIds.get(INDEX), "del", instanceIds.get(DELETE));
  }

  private byte[] prepareDocumentBody(SubjectResourceEvent payload, Map<IndexActionType, Set<String>> instanceIds) {
    var resource = new SubjectResource();
    resource.setId(payload.getId());
    resource.setValue(payload.getValue());
    resource.setInstances(subtract(instanceIds.get(INDEX), instanceIds.get(DELETE)));
    resource.setAuthorityId(payload.getAuthorityId());
    return BytesReference.toBytes(
      searchDocumentBodyConverter.apply(jsonConverter.convert(resource, new TypeReference<>() {
      })));
  }

  private SubjectResourceEvent getPayload(SearchDocumentBody doc) {
    return jsonConverter.fromJson(jsonConverter.toJson(getEventPayload(doc.getResourceEvent())),
      SubjectResourceEvent.class);
  }
}
