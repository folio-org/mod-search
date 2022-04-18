package org.folio.search.converter;

import org.folio.search.domain.dto.StreamIdsJob;
import org.folio.search.model.streamids.StreamIdsJobEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface StreamIdsJobMapper {

  StreamIdsJobEntity convert(StreamIdsJob job);

  @Mapping(target = "createdDate", dateFormat = "yyyy-MM-dd HH:mm")
  StreamIdsJob convert(StreamIdsJobEntity entity);
}
