package org.folio.search.converter;

import org.folio.search.domain.dto.CqlFacetRequest;
import org.folio.search.domain.dto.CqlSearchRequest;
import org.folio.search.domain.dto.SuggestRequest;
import org.folio.search.model.service.CqlFacetServiceRequest;
import org.folio.search.model.service.CqlSearchServiceRequest;
import org.folio.search.model.service.SuggestServiceRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

@Component
@Mapper(componentModel = "spring")
public interface SearchRequestConverter {

  /**
   * Converts cqlSearchRequest, resource name and tenantId into {@link CqlSearchServiceRequest} object.
   *
   * @param request cql search request as {@link CqlSearchRequest}
   * @param resource resource name as {@link String}
   * @param tenantId tenant id as {@link String}
   * @return created {@link CqlSearchServiceRequest} object
   */
  @Mapping(source = "resource", target = "resource")
  @Mapping(source = "tenantId", target = "tenantId")
  CqlSearchServiceRequest convert(CqlSearchRequest request, String resource, String tenantId);

  /**
   * Converts cqlFacetRequest, resource name and tenantId into {@link CqlFacetServiceRequest} object.
   *
   * @param request cql facet request as {@link CqlFacetRequest}
   * @param resource resource name as {@link String}
   * @param tenantId tenant id as {@link String}
   * @return created {@link CqlFacetServiceRequest} object
   */
  @Mapping(source = "resource", target = "resource")
  @Mapping(source = "tenantId", target = "tenantId")
  CqlFacetServiceRequest convert(CqlFacetRequest request, String resource, String tenantId);

  /**
   * Converts suggest request, resource name and tenantId into {@link SuggestServiceRequest} object.
   *
   * @param request suggest request as {@link SuggestRequest}
   * @param resource resource name as {@link String}
   * @param tenantId tenant id as {@link String}
   * @return created {@link SuggestServiceRequest} object
   */
  @Mapping(source = "resource", target = "resource")
  @Mapping(source = "tenantId", target = "tenantId")
  SuggestServiceRequest convert(SuggestRequest request, String resource, String tenantId);
}
