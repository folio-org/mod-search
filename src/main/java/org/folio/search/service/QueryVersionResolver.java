package org.folio.search.service;

import lombok.extern.log4j.Log4j2;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.service.QueryResolution;
import org.folio.search.model.types.QueryVersion;
import org.folio.search.model.types.ResourceType;
import org.folio.search.repository.IndexNameProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class QueryVersionResolver {

  private final IndexFamilyService indexFamilyService;
  private final IndexNameProvider indexNameProvider;
  private final String defaultVersion;

  public QueryVersionResolver(IndexFamilyService indexFamilyService,
                              IndexNameProvider indexNameProvider,
                              @Value("${folio.search.default-query-version:1}") String defaultVersion) {
    this.indexFamilyService = indexFamilyService;
    this.indexNameProvider = indexNameProvider;
    this.defaultVersion = defaultVersion;
  }

  public QueryResolution resolve(String version, String tenantId) {
    var qv = QueryVersion.fromString(resolveVersion(version));

    var activeFamily = indexFamilyService.findActiveFamily(tenantId, qv);
    if (activeFamily.isPresent()) {
      var alias = indexFamilyService.getAliasName(tenantId, qv);
      log.debug("resolve:: path via active family [version: {}, tenant: {}, alias: {}]",
        qv.getValue(), tenantId, alias);
      return new QueryResolution(alias, qv.getPathType());
    }

    if (qv == QueryVersion.V1) {
      var legacyIndex = indexNameProvider.getIndexName(ResourceType.INSTANCE, tenantId);
      if (indexFamilyService.physicalIndexExists(legacyIndex)) {
        log.debug("resolve:: LEGACY fallback (no V1 family) [version: {}, tenant: {}, index: {}]",
          qv.getValue(), tenantId, legacyIndex);
        return new QueryResolution(legacyIndex, QueryResolution.PathType.LEGACY);
      }
    }

    throw new RequestValidationException(
      "No ACTIVE index family for version " + qv.getValue(), "queryVersion", qv.getValue());
  }

  public String resolveVersion(String version) {
    return version != null ? version : getDefaultVersion();
  }

  public String getDefaultVersion() {
    return defaultVersion;
  }
}
