package org.folio.search.service.config;

import static org.folio.search.client.InventoryReferenceDataClient.ReferenceDataType.CLASSIFICATION_TYPES;
import static org.folio.search.configuration.SearchCacheNames.BROWSE_CONFIG_CACHE;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.SetUtils;
import org.folio.search.converter.BrowseConfigMapper;
import org.folio.search.domain.dto.BrowseConfig;
import org.folio.search.domain.dto.BrowseConfigCollection;
import org.folio.search.domain.dto.BrowseOptionType;
import org.folio.search.domain.dto.BrowseType;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.integration.folio.ReferenceDataService;
import org.folio.search.model.client.CqlQueryParam;
import org.folio.search.model.config.BrowseConfigEntity;
import org.folio.search.model.config.BrowseConfigId;
import org.folio.search.repository.BrowseConfigEntityRepository;
import org.folio.search.utils.CollectionUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@RequiredArgsConstructor
public class BrowseConfigService {

  private static final String ID_VALIDATION_MSG = "Body doesn't match path parameter: %s";
  private static final String TYPE_IDS_VALIDATION_MSG = "Classification type IDs don't exist";

  private final ReferenceDataService referenceDataService;
  private final BrowseConfigEntityRepository repository;
  private final BrowseConfigMapper mapper;

  public BrowseConfigCollection getConfigs(@NonNull BrowseType type) {
    log.debug("Fetch browse configuration [browseType: {}]", type.getValue());
    return mapper.convert(repository.findByConfigId_BrowseType(type.getValue()));
  }

  @Cacheable(cacheNames = BROWSE_CONFIG_CACHE,
             key = "@folioExecutionContext.tenantId + ':' + #type.value + ':' + #optionType.value")
  public BrowseConfig getConfig(@NonNull BrowseType type, @NonNull BrowseOptionType optionType) {
    var typeValue = type.getValue();
    var optionTypeValue = optionType.getValue();
    log.debug("Fetch browse configuration [browseType: {}, browseOptionType: {}]", typeValue, optionTypeValue);

    return repository.findById(new BrowseConfigId(typeValue, optionTypeValue))
      .map(mapper::convert)
      .orElseThrow(() -> new IllegalStateException(
        "Config for %s type %s must be present in database".formatted(typeValue, optionTypeValue)));
  }

  @CacheEvict(cacheNames = BROWSE_CONFIG_CACHE, allEntries = true)
  public void upsertConfig(@NonNull BrowseType type,
                           @NonNull BrowseOptionType optionType,
                           @NonNull BrowseConfig config) {
    validateConfig(optionType, config);

    log.debug("Update browse configuration option [browseType: {}, browseOptionType: {}, newValue: {}]",
      type.getValue(), optionType.getValue(), config);

    var configEntity = mapper.convert(type, config);
    repository.save(configEntity);
  }

  @Transactional
  @CacheEvict(cacheNames = BROWSE_CONFIG_CACHE, allEntries = true)
  public void deleteTypeIdsFromConfigs(@NonNull BrowseType type, @NonNull List<String> typeIds) {
    if (typeIds.isEmpty()) {
      return;
    }
    var configs = repository.findByConfigId_BrowseType(type.getValue());
    for (BrowseConfigEntity config : configs) {
      var newTypeIds = Optional.ofNullable(config.getTypeIds())
        .map(ArrayList::new)
        .orElse(new ArrayList<>());
      newTypeIds.removeAll(typeIds);
      config.setTypeIds(newTypeIds);
    }
    repository.saveAll(configs);
  }

  private void validateConfig(BrowseOptionType optionType, BrowseConfig config) {
    validateOptionType(optionType, config);
    validateTypeIds(config);
  }

  private void validateTypeIds(BrowseConfig config) {
    var ids = CollectionUtils.toStreamSafe(config.getTypeIds()).map(UUID::toString).collect(Collectors.toSet());
    var existedIds = referenceDataService.fetchReferenceData(CLASSIFICATION_TYPES, CqlQueryParam.ID, ids);
    var difference = SetUtils.difference(ids, existedIds);
    if (!difference.isEmpty()) {
      throw new RequestValidationException(TYPE_IDS_VALIDATION_MSG, "typeIds", difference.toString());
    }
  }

  private static void validateOptionType(BrowseOptionType optionType, BrowseConfig config) {
    if (optionType != config.getId()) {
      throw new RequestValidationException(
        ID_VALIDATION_MSG.formatted(optionType.getValue()), "id", config.getId().toString());
    }
  }
}
