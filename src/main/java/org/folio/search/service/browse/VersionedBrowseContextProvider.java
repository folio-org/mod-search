package org.folio.search.service.browse;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.model.service.BrowseContext;
import org.folio.search.model.service.BrowseRequest;
import org.folio.search.model.types.QueryVersion;
import org.folio.search.service.IndexFamilyService;
import org.folio.search.service.QueryVersionResolver;
import org.folio.search.utils.V2BrowseIndexNameResolver;
import org.springframework.stereotype.Component;

/**
 * Version-aware browse context. LEGACY delegates to BrowseContextProvider.
 * FLAT uses FlatSearchQueryConverter for structural validation.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class VersionedBrowseContextProvider {

  private final BrowseContextProvider browseContextProvider;
  private final QueryVersionResolver queryVersionResolver;
  private final IndexFamilyService indexFamilyService;

  public BrowseContext get(BrowseRequest request, String queryVersion) {
    return browseContextProvider.get(request);
  }

  /**
   * Resolves a browse request, potentially swapping the resource type for V2 browse.
   * When V2 is active and the request targets a V1 browse resource type, the resource type is
   * swapped to its V2 equivalent so downstream queries hit the V2 browse alias.
   */
  public BrowseRequest resolveBrowseRequest(BrowseRequest request) {
    var version = queryVersionResolver.resolveVersion(request.getQueryVersion());
    var qv = QueryVersion.fromString(version);

    if (qv != QueryVersion.V2 || !V2BrowseIndexNameResolver.isV1BrowseType(request.getResource())) {
      return request;
    }

    var v2Family = indexFamilyService.findActiveFamily(request.getTenantId(), QueryVersion.V2);
    if (v2Family.isEmpty()) {
      return request;
    }

    var v2BrowseType = V2BrowseIndexNameResolver.resolveV2BrowseType(request.getResource());
    log.debug("resolveBrowseRequest:: swapping resource type [from: {}, to: {}, tenant: {}]",
      request.getResource(), v2BrowseType, request.getTenantId());

    return request.toBuilder()
      .resource(v2BrowseType)
      .build();
  }
}
