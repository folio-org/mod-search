package org.folio.search.service.es;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.exception.ResourceDescriptionException;
import org.folio.search.service.LocalFileProvider;
import org.folio.search.utils.JsonConverter;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class SearchSettingsHelper {

  private final LocalFileProvider localFileProvider;
  private final JsonConverter jsonConverter;

  /**
   * Provides elasticsearch settings for given resource name.
   *
   * @param resource resource name as {@link String} object
   * @return elasticsearch settings as {@link String} object with JSON object inside
   */
  public String getSettings(String resource) {
    var resourceSettings = localFileProvider.read(getIndexSettingsPath(resource));
    if (!jsonConverter.isValidJsonString(resourceSettings)) {
      throw new ResourceDescriptionException(String.format(
        "Failed to load resource index settings [resourceName: %s]", resource));
    }
    return resourceSettings;
  }

  private static String getIndexSettingsPath(String resource) {
    return "elasticsearch/index/" + resource + ".json";
  }
}
