package org.folio.search.model.service;

import java.util.List;
import lombok.Builder;
import org.folio.search.domain.dto.BrowseOptionType;
import org.folio.search.model.ResourceRequest;
import org.folio.search.model.types.ResourceType;

/**
 * Browse request model.
 *
 * @param resource              Resource name.
 * @param tenantId              Request tenant id.
 * @param browseOptionType      Browse option type.
 * @param query                 A CQL query string with search conditions.
 * @param limit                 Limit the number of elements in the response.
 * @param targetField           Target field for browsing.
 * @param expandAll             Whether to return only basic properties or entire instance.
 * @param include               Retrieves the fields listed in the specified parameter.
 * @param highlightMatch        Whether to highlight matched resources or not.
 * @param precedingRecordsCount Number of preceding records for virtual shelf browsing. Works only when browsing around.
 */
@Builder
public record BrowseRequest(
  ResourceType resource,
  String tenantId,
  BrowseOptionType browseOptionType,
  String query,
  Integer limit,
  String targetField,
  Boolean expandAll,
  String include,
  Boolean highlightMatch,
  Integer precedingRecordsCount
) implements ResourceRequest {

  public List<String> getIncludeFields() {
    return ResourceRequest.parseIncludeField(this.include);
  }
}
