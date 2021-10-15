package org.folio.search.converter;

import org.folio.search.domain.dto.FeatureConfig;
import org.folio.search.domain.dto.TenantConfiguredFeature;
import org.folio.search.model.config.FeatureConfigEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = TenantConfiguredFeature.class)
public interface FeatureConfigMapper {

  /**
   * Converts {@link FeatureConfig} object to {@link FeatureConfigEntity} object.
   *
   * @param config - dto object to convert as {@link FeatureConfig} object
   * @return converted {@link FeatureConfigEntity} object
   */
  @Mapping(source = "feature.value", target = "featureId")
  FeatureConfigEntity convert(FeatureConfig config);

  /**
   * Converts {@link FeatureConfigEntity} object to {@link FeatureConfig} DTO object.
   *
   * @param entity - entity to convert as {@link FeatureConfigEntity} object.
   * @return converted {@link FeatureConfig} dto object
   */
  @Mapping(target = "feature", expression = "java(TenantConfiguredFeature.fromValue(entity.getFeatureId()))")
  FeatureConfig convert(FeatureConfigEntity entity);
}
