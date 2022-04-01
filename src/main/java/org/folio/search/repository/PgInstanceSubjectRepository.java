package org.folio.search.repository;

import static org.folio.search.utils.SearchConverterUtils.getNewAsMap;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;

import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections.MapUtils;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.model.entity.InstanceSubjectEntity;
import org.folio.search.model.entity.InstanceSubjectEntityId;
import org.folio.search.model.index.SearchDocumentBody;
import org.folio.search.service.TenantScopedExecutionService;
import org.folio.search.utils.JsonConverter;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PgInstanceSubjectRepository extends AbstractResourceRepository {

  private final JsonConverter jsonConverter;
  private final InstanceSubjectJpaRepository instanceSubjectJpaRepository;
  private final TenantScopedExecutionService tenantScopedExecutionService;

  @Override
  public FolioIndexOperationResponse indexResources(List<SearchDocumentBody> documents) {
    var docsByTenant = documents.stream().collect(Collectors.groupingBy(SearchDocumentBody::getRouting));
    for (var entry : docsByTenant.entrySet()) {
      tenantScopedExecutionService.executeTenantScoped(entry.getKey(), () -> {
        var entities = entry.getValue().stream()
          .map(this::mapToEntity)
          .distinct()
          .collect(Collectors.toList());
        return instanceSubjectJpaRepository.saveAll(entities);
      });
    }

    return getSuccessIndexOperationResponse();
  }

  private InstanceSubjectEntity mapToEntity(SearchDocumentBody body) {
    var instanceId = MapUtils.getString(getNewAsMap(body.getResourceEvent()), "instanceId");
    var subject = jsonConverter.asJsonTree(body.getRawJson()).path("subject").asText();
    return InstanceSubjectEntity.of(InstanceSubjectEntityId.of(subject, instanceId));
  }
}
