package org.folio.search.converter;

import org.folio.search.domain.dto.ResourceIdsJob;
import org.folio.search.model.streamids.ResourceIdsJobEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ResourceIdsJobMapper {

  ResourceIdsJobEntity convert(ResourceIdsJob job);

  @Mapping(target = "createdDate", source = "createdDate", dateFormat = "yyyy-MM-dd HH:mm")
  ResourceIdsJob convert(ResourceIdsJobEntity entity);
}
