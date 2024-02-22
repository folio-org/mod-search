package org.folio.search.converter;

import java.util.List;
import org.folio.search.domain.dto.BrowseConfig;
import org.folio.search.domain.dto.BrowseConfigCollection;
import org.folio.search.domain.dto.BrowseOptionType;
import org.folio.search.domain.dto.BrowseType;
import org.folio.search.domain.dto.ShelvingOrderAlgorithmType;
import org.folio.search.model.config.BrowseConfigEntity;
import org.folio.search.model.config.BrowseConfigId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring",
        imports = {BrowseOptionType.class, BrowseConfigId.class, ShelvingOrderAlgorithmType.class})
public interface BrowseConfigMapper {

  @Mapping(target = "configId", expression = "java(new BrowseConfigId(type.getValue(), config.getId().getValue()))")
  @Mapping(target = "shelvingAlgorithm", expression = "java(config.getShelvingAlgorithm().getValue())")
  BrowseConfigEntity convert(BrowseType type, BrowseConfig config);

  @Mapping(target = "id", expression = "java(BrowseOptionType.fromValue(source.getConfigId().getBrowseOptionType()))")
  @Mapping(target = "shelvingAlgorithm",
           expression = "java(ShelvingOrderAlgorithmType.fromValue(source.getShelvingAlgorithm()))")
  BrowseConfig convert(BrowseConfigEntity source);

  default BrowseConfigCollection convert(List<BrowseConfigEntity> entities) {
    return map(new BrowseConfigWrapper(entities));
  }

  @Mapping(target = "totalRecords", expression = "java(source.configs().size())")
  BrowseConfigCollection map(BrowseConfigWrapper source);

  record BrowseConfigWrapper(List<BrowseConfigEntity> configs) { }
}
