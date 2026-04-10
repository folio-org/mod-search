package org.folio.search.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.service.QueryResolution;
import org.folio.search.model.types.QueryVersion;
import org.folio.search.model.types.ResourceType;
import org.folio.search.repository.IndexNameProvider;
import org.folio.search.service.reindex.jdbc.QueryVersionConfigRepository;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class QueryVersionResolver {

  private final IndexFamilyService indexFamilyService;
  private final IndexNameProvider indexNameProvider;
  private final QueryVersionConfigRepository configRepository;

  public QueryResolution resolve(String version, String tenantId) {
    var qv = QueryVersion.fromString(resolveVersion(version, tenantId));

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

  public String resolveVersion(String version, String tenantId) {
    return version != null ? version : getDefaultVersion(tenantId);
  }

  public String getDefaultVersion(String tenantId) {
    return configRepository.getDefaultVersion(tenantId).orElse(QueryVersion.getDefault().getValue());
  }

  public void setDefaultVersion(String version, String tenantId) {
    var qv = QueryVersion.fromString(version);

    if (!isUsableVersion(tenantId, qv)) {
      if (qv == QueryVersion.V1) {
        throw new RequestValidationException(
          "Cannot set default to version " + version + ": no ACTIVE family and no legacy index", "version", version);
      }

      throw new RequestValidationException(
        "Cannot set default to version " + version + ": no ACTIVE family exists", "version", version);
    }

    configRepository.upsertDefaultVersion(tenantId, version);
    log.info("setDefaultVersion:: set default version [version: {}, tenant: {}]", version, tenantId);
  }

  private boolean isUsableVersion(String tenantId, QueryVersion queryVersion) {
    if (indexFamilyService.findActiveFamily(tenantId, queryVersion).isPresent()) {
      return true;
    }

    return queryVersion == QueryVersion.V1
      && indexFamilyService.physicalIndexExists(indexNameProvider.getIndexName(ResourceType.INSTANCE, tenantId));
  }
}
