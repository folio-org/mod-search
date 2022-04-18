package org.folio.search.converter;

import org.folio.search.domain.dto.FeatureConfig;
import org.folio.search.domain.dto.StreamIdsJob;
import org.folio.search.domain.dto.TenantConfiguredFeature;
import org.folio.search.model.config.FeatureConfigEntity;
import org.folio.search.model.streamids.StreamIdsJobEntity;
import org.folio.search.model.streamids.StreamJobStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface StreamIdsJobMapper {

  /**
   * Converts {@link FeatureConfig} object to {@link FeatureConfigEntity} object.
   *
   * @param config - dto object to convert as {@link FeatureConfig} object
   * @return converted {@link FeatureConfigEntity} object
   */
  StreamIdsJobEntity convert(StreamIdsJob job);

  /**
   * Converts {@link FeatureConfigEntity} object to {@link FeatureConfig} DTO object.
   *
   * @param entity - entity to convert as {@link FeatureConfigEntity} object.
   * @return converted {@link FeatureConfig} dto object
   */
  @Mapping(target = "createdDate", dateFormat = "yyyy-MM-dd HH:mm")
  StreamIdsJob convert(StreamIdsJobEntity entity);
}
