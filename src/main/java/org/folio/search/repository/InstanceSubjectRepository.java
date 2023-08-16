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
import java.util.HashMap;
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
import org.folio.search.service.consortium.ConsortiumTenantService;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.script.Script;
import org.springframework.stereotype.Repository;

@Log4j2
@Repository
@RequiredArgsConstructor
public class InstanceSubjectRepository extends AbstractResourceRepository {

  private static final String INSTANCE_ID = "instanceId";

  private final SearchConfigurationProperties properties;
  private final ConsortiumTenantService consortiumTenantService;

  @Override
  public FolioIndexOperationResponse indexResources(List<SearchDocumentBody> documentBodies) {
    var bulkRequest = new BulkRequest();

    var docsById = documentBodies.stream().collect(groupingBy(SearchDocumentBody::getId));
    for (var entry : docsById.entrySet()) {
      var documents = entry.getValue();
      var instances = prepareInstances(documents);
      if (!IterableUtils.matchesAll(instances.values(), Set::isEmpty)) {
        var upsertRequest = prepareUpsertRequest(documents.iterator().next(), instances);
        bulkRequest.add(upsertRequest);
      }
    }

    var bulkApiResponse = executeBulkRequest(bulkRequest);

    return bulkApiResponse.hasFailures()
           ? getErrorIndexOperationResponse(bulkApiResponse.buildFailureMessage())
           : getSuccessIndexOperationResponse();
  }

  private EnumMap<IndexActionType, Set<Map<String, Object>>> prepareInstances(List<SearchDocumentBody> documents) {
    var instances = prepareInstanceMap();
    for (var document : documents) {
      var payload = getPayload(document);
      var instanceId = String.valueOf(payload.get(INSTANCE_ID));
      if (StringUtils.isNotBlank(instanceId)) {
        var tenantId = document.getTenant();
        var instance = new HashMap<String, Object>();
        instance.put(INSTANCE_ID, instanceId);
        instance.put("tenantId", tenantId);
        consortiumTenantService.getCentralTenant(tenantId).ifPresent(centralTenant ->
          instance.put("shared", centralTenant.equals(tenantId)));

        instances.getOrDefault(document.getAction(), instances.get(DELETE)).add(instance);
      } else {
        log.warn("InstanceId is blank in subject event. [payload: {}]", payload);
      }
    }
    return instances;
  }

  private EnumMap<IndexActionType, Set<Map<String, Object>>> prepareInstanceMap() {
    var instanceIds = new EnumMap<IndexActionType, Set<Map<String, Object>>>(IndexActionType.class);
    instanceIds.put(INDEX, new HashSet<>());
    instanceIds.put(DELETE, new HashSet<>());
    return instanceIds;
  }

  private UpdateRequest prepareUpsertRequest(SearchDocumentBody doc,
                                             EnumMap<IndexActionType, Set<Map<String, Object>>> instances) {
    return new UpdateRequest()
      .id(doc.getId())
      .scriptedUpsert(true)
      .retryOnConflict(properties.getIndexing().getInstanceSubjects().getRetryAttempts())
      .index(indexNameProvider.getIndexName(doc))
      .script(new Script(STORED, null, INSTANCE_SUBJECT_UPSERT_SCRIPT_ID, prepareScriptParams(instances)))
      .upsert(prepareDocumentBody(getPayload(doc), instances), doc.getDataFormat().getXcontentType());
  }

  private Map<String, Object> prepareScriptParams(EnumMap<IndexActionType, Set<Map<String, Object>>> instanceIds) {
    return Map.of("ins", instanceIds.get(INDEX), "del", instanceIds.get(DELETE));
  }

  private Map<String, Object> prepareDocumentBody(Map<String, Object> payload,
                                                  Map<IndexActionType, Set<Map<String, Object>>> instances) {
    payload.put("instances", subtract(instances.get(INDEX), instances.get(DELETE)));
    payload.remove(INSTANCE_ID);
    return payload;
  }

  private Map<String, Object> getPayload(SearchDocumentBody doc) {
    return getEventPayload(doc.getResourceEvent());
  }
}
