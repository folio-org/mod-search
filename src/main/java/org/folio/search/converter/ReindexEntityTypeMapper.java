package org.folio.search.converter;

import java.util.List;
import org.folio.search.domain.dto.ReindexUploadDto;
import org.folio.search.model.types.ReindexEntityType;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ReindexEntityTypeMapper {

  ReindexEntityType convert(ReindexUploadDto.EntityTypesEnum entityTypesDto);

  default List<ReindexEntityType> convert(List<ReindexUploadDto.EntityTypesEnum> entityTypesDto) {
    return entityTypesDto.stream().map(this::convert).toList();
  }
}
