package org.folio.search.service.indexrebuild.callnumber;

import static org.folio.search.utils.SearchUtils.CALL_NUMBER_FIELD;
import static org.folio.search.utils.SearchUtils.CALL_NUMBER_ID_FIELD;
import static org.folio.search.utils.SearchUtils.CALL_NUMBER_PREFIX_FIELD;
import static org.folio.search.utils.SearchUtils.CALL_NUMBER_SUFFIX_FIELD;
import static org.folio.search.utils.SearchUtils.CALL_NUMBER_TYPE_ID_FIELD;
import static org.folio.search.utils.SearchUtils.EFFECTIVE_LOCATION_ID_FIELD;
import static org.folio.search.utils.SearchUtils.ID_FIELD;
import static org.folio.search.utils.SearchUtils.INSTANCE_ID_FIELD;
import static org.folio.search.utils.SearchUtils.SHARED_FIELD_NAME;
import static org.folio.search.utils.SearchUtils.SUB_RESOURCE_INSTANCES_FIELD;
import static org.folio.search.utils.SearchUtils.TENANT_ID_FIELD_NAME;
import static org.opensearch.index.query.QueryBuilders.nestedQuery;
import static org.opensearch.index.query.QueryBuilders.termQuery;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.lucene.search.join.ScoreMode;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.domain.dto.TenantConfiguredFeature;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.service.FeatureConfigService;
import org.folio.search.service.indexrebuild.IndexRebuildTarget;
import org.folio.search.service.indexrebuild.derived.DerivedRebuildStrategy;
import org.opensearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

/**
 * Call number index derived from the item index: one document per call number, keyed by the {@code callNumberId} set
 * on item documents.
 */
@Component
@RequiredArgsConstructor
public class CallNumberRebuildStrategy implements DerivedRebuildStrategy {

  private static final String[] ITEM_FIELDS = {CALL_NUMBER_ID_FIELD, CALL_NUMBER_FIELD, CALL_NUMBER_PREFIX_FIELD,
    CALL_NUMBER_SUFFIX_FIELD, CALL_NUMBER_TYPE_ID_FIELD, INSTANCE_ID_FIELD, TENANT_ID_FIELD_NAME, SHARED_FIELD_NAME,
    EFFECTIVE_LOCATION_ID_FIELD};

  private final FeatureConfigService featureConfigService;
  private final SearchConfigurationProperties searchConfiguration;

  @Override
  public IndexRebuildTarget target() {
    return IndexRebuildTarget.CALL_NUMBER;
  }

  @Override
  public IndexRebuildTarget source() {
    return IndexRebuildTarget.ITEM;
  }

  @Override
  public boolean isEnabled() {
    return featureConfigService.isEnabled(TenantConfiguredFeature.BROWSE_CALL_NUMBERS);
  }

  @Override
  public void validateEnabled() {
    if (!isEnabled()) {
      throw new RequestValidationException("Browsing call numbers is not enabled", "feature",
        TenantConfiguredFeature.BROWSE_CALL_NUMBERS.getValue());
    }
  }

  @Override
  public boolean isTrackingEnabled() {
    return Boolean.TRUE.equals(searchConfiguration.getIndexing().getCallNumberIndexFromItemsEnabled());
  }

  @Override
  public String keyField() {
    return CALL_NUMBER_ID_FIELD;
  }

  @Override
  public String[] sourceFields() {
    return ITEM_FIELDS.clone();
  }

  @Override
  public Set<String> keysOf(Map<?, ?> sourceRecord) {
    return CallNumberKey.fromItem(sourceRecord).map(key -> Set.of(key.id())).orElse(Set.of());
  }

  @Override
  public String documentKeyField() {
    return ID_FIELD;
  }

  @Override
  public QueryBuilder documentsOfTenantQuery(String tenantId) {
    return nestedQuery(SUB_RESOURCE_INSTANCES_FIELD,
      termQuery(SUB_RESOURCE_INSTANCES_FIELD + "." + TENANT_ID_FIELD_NAME, tenantId), ScoreMode.None);
  }

  @Override
  public Optional<Map<String, Object>> build(List<Map<String, Object>> sourceDocuments) {
    return CallNumberDocumentBuilder.build(sourceDocuments);
  }
}
