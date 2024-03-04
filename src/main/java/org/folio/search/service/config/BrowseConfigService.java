package org.folio.search.service.config;

import static org.folio.search.configuration.SearchCacheNames.BROWSE_CONFIG_CACHE;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.converter.BrowseConfigMapper;
import org.folio.search.domain.dto.BrowseConfig;
import org.folio.search.domain.dto.BrowseConfigCollection;
import org.folio.search.domain.dto.BrowseOptionType;
import org.folio.search.domain.dto.BrowseType;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.config.BrowseConfigId;
import org.folio.search.repository.BrowseConfigEntityRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class BrowseConfigService {

  private static final String BODY_VALIDATION_MSG = "Body doesn't match path parameter: %s";

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

  @CacheEvict(cacheNames = BROWSE_CONFIG_CACHE,
              key = "@folioExecutionContext.tenantId + ':' + #type.value + ':' + #optionType.value")
  public void upsertConfig(@NonNull BrowseType type,
                           @NonNull BrowseOptionType optionType,
                           @NonNull BrowseConfig config) {
    if (optionType != config.getId()) {
      throw new RequestValidationException(
        BODY_VALIDATION_MSG.formatted(optionType.getValue()), "id", config.getId().toString());
    }
    log.debug("Update browse configuration option [browseType: {}, browseOptionType: {}, newValue: {}]",
      type.getValue(), optionType.getValue(), config);

    var configEntity = mapper.convert(type, config);
    repository.save(configEntity);
  }
}
