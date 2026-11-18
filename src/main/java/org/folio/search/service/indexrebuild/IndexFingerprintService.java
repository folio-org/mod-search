package org.folio.search.service.indexrebuild;

import lombok.RequiredArgsConstructor;
import org.folio.search.service.es.SearchMappingsHelper;
import org.folio.search.service.es.SearchSettingsHelper;
import org.folio.search.utils.ShaUtils;
import org.springframework.stereotype.Service;

/**
 * Calculates what a rebuilt index is expected to look like in the running version of the module. Only settings that
 * can't be applied without a rebuild are included, so shard, replica and refresh settings don't affect the result.
 */
@Service
@RequiredArgsConstructor
public class IndexFingerprintService {

  private static final String ANALYSIS_SETTINGS = "analysis";

  private final SearchMappingsHelper mappingsHelper;
  private final SearchSettingsHelper settingsHelper;

  public String current(IndexRebuildTarget target) {
    var resourceType = target.getResourceType();
    return ShaUtils.sha(mappingsHelper.getMappings(resourceType),
      settingsHelper.getSettingsJson(resourceType).path(ANALYSIS_SETTINGS).toString(),
      String.valueOf(target.getDataVersion()));
  }
}
