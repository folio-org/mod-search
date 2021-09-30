package org.folio.search.mapper;

import java.util.List;
import org.folio.search.model.service.CqlFacetServiceRequest;
import org.folio.search.model.service.CqlSearchServiceRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SearchRequestMapper {

  /**
   * Converts cqlSearchRequest, resource name and tenantId into {@link CqlSearchServiceRequest} object.
   *
   * @param query CQL query string with search conditions {@link String}
   * @param limit  limit the number of elements returned in the response {@link Integer}
   * @param offset  skip over a number of elements by specifying an offset value for the query {@link Integer}
   * @param expandAll  whether to return only basic properties or entire instance {@link Boolean}
   * @param resource resource name as {@link String}
   * @param tenantId tenant id as {@link String}
   * @return created {@link CqlSearchServiceRequest} object
   */
  @Mapping(source = "resource", target = "resource")
  @Mapping(source = "tenantId", target = "tenantId")
  CqlSearchServiceRequest convert(String query, Integer limit, Integer offset, Boolean expandAll, String resource,
                                  String tenantId);

  /**
   * Converts cqlFacetRequest, resource name and tenantId into {@link CqlFacetServiceRequest} object.
   *
   * @param query  CQL query string with search conditions {@link String}
   * @param facet  List of facet names {@link List}
   * @param resource resource name as {@link String}
   * @param tenantId tenant id as {@link String}
   * @return created {@link CqlFacetServiceRequest} object
   */
  @Mapping(source = "resource", target = "resource")
  @Mapping(source = "tenantId", target = "tenantId")
  CqlFacetServiceRequest convert(String query, List<String> facet, String resource, String tenantId);
}
