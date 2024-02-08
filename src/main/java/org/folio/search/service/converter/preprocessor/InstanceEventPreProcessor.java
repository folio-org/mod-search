package org.folio.search.service.converter.preprocessor;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.apache.commons.collections4.MapUtils.getObject;
import static org.apache.commons.lang3.StringUtils.startsWith;
import static org.folio.search.utils.CollectionUtils.subtract;
import static org.folio.search.utils.SearchConverterUtils.getNewAsMap;
import static org.folio.search.utils.SearchConverterUtils.getOldAsMap;
import static org.folio.search.utils.SearchConverterUtils.getResourceEventId;
import static org.folio.search.utils.SearchConverterUtils.getResourceSource;
import static org.folio.search.utils.SearchUtils.CLASSIFICATIONS_FIELD;
import static org.folio.search.utils.SearchUtils.CLASSIFICATION_NUMBER_FIELD;
import static org.folio.search.utils.SearchUtils.CLASSIFICATION_TYPE_FIELD;
import static org.folio.search.utils.SearchUtils.SOURCE_CONSORTIUM_PREFIX;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.MapUtils;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.TenantConfiguredFeature;
import org.folio.search.repository.classification.InstanceClassificationEntity;
import org.folio.search.repository.classification.InstanceClassificationRepository;
import org.folio.search.service.FeatureConfigService;
import org.folio.search.service.consortium.ConsortiumTenantService;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class InstanceEventPreProcessor implements EventPreProcessor {

  private final FeatureConfigService featureConfigService;
  private final ConsortiumTenantService consortiumTenantService;
  private final InstanceClassificationRepository instanceClassificationRepository;

  @Override
  public List<ResourceEvent> preProcess(ResourceEvent event) {
    log.info("preProcess::Starting instance event pre-processing");
    if (log.isDebugEnabled()) {
      log.debug("preProcess::Starting instance event pre-processing [{}]", event);
    }
    if (startsWith(getResourceSource(event), SOURCE_CONSORTIUM_PREFIX)) {
      log.info("preProcess::Finished instance event pre-processing. No additional events created for shadow instance.");
      return List.of(event);
    }

    var events = processClassifications(event);

    log.info("preProcess::Finished instance event pre-processing");
    if (log.isDebugEnabled()) {
      log.debug("preProcess::Finished instance event pre-processing. Events after: [{}], ", events);
    }
    return events;
  }

  private List<ResourceEvent> processClassifications(ResourceEvent event) {
    if (!featureConfigService.isEnabled(TenantConfiguredFeature.BROWSE_CLASSIFICATIONS)) {
      return List.of(event);
    }

    var oldClassifications = getClassifications(getOldAsMap(event));
    var newClassifications = getClassifications(getNewAsMap(event));

    if (oldClassifications.equals(newClassifications)) {
      return List.of(event);
    }

    var tenant = event.getTenant();
    var instanceId = getResourceEventId(event);
    var shared = isShared(tenant);

    var classificationsForCreate = subtract(newClassifications, oldClassifications);
    var classificationsForDelete = subtract(oldClassifications, newClassifications);

    instanceClassificationRepository.saveAll(toEntities(classificationsForCreate, instanceId, tenant, shared));
    instanceClassificationRepository.deleteAll(toEntities(classificationsForDelete, instanceId, tenant, shared));

    return List.of(event);
  }

  @NotNull
  private static List<InstanceClassificationEntity> toEntities(
    Set<Map<String, Object>> subtract, String instanceId, String tenantId, boolean shared) {
    return subtract.stream()
      .map(map -> {
        var classificationId = InstanceClassificationEntity.Id.builder()
          .number(MapUtils.getString(map, CLASSIFICATION_NUMBER_FIELD))
          .type(MapUtils.getString(map, CLASSIFICATION_TYPE_FIELD))
          .instanceId(instanceId)
          .tenantId(tenantId)
          .build();
        return new InstanceClassificationEntity(classificationId, shared);
      })
      .toList();
  }

  @SuppressWarnings("unchecked")
  private Set<Map<String, Object>> getClassifications(Map<String, Object> event) {
    var object = getObject(event, CLASSIFICATIONS_FIELD, emptyList());
    if (object == null) {
      return emptySet();
    }
    return new HashSet<>((List<Map<String, Object>>) object);
  }

  private boolean isShared(String tenantId) {
    var centralTenant = consortiumTenantService.getCentralTenant(tenantId);
    return centralTenant.isPresent() && centralTenant.get().equals(tenantId);
  }
}
