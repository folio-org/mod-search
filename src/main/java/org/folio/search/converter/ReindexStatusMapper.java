package org.folio.search.converter;

import org.folio.search.domain.dto.ReindexStatusItem;
import org.folio.search.model.reindex.ReindexStatusEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ReindexStatusMapper {

  ReindexStatusItem convert(ReindexStatusEntity entity);
}
