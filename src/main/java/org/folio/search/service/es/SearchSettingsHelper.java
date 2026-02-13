package org.folio.search.service.es;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.SerializationException;
import org.folio.search.exception.ResourceDescriptionException;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.metadata.LocalFileProvider;
import org.folio.search.utils.JsonConverter;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

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
  public String getSettings(ResourceType resource) {
    return getSettingsJson(resource).toString();
  }

  /**
   * Provides elasticsearch settings for given resource name.
   *
   * @param resource resource name as {@link String} object
   * @return elasticsearch settings as {@link JsonNode} object
   */
  public JsonNode getSettingsJson(ResourceType resource) {
    log.debug("getSettings:: by [resource: {}]", resource);

    return loadSettings(resource.getName());
  }

  public JsonNode getDynamicSettings() {
    log.debug("getDynamicSettings::try to load dynamic index settings");

    var settingsName = "dynamicSettings";
    return loadSettings(settingsName);
  }

  private JsonNode loadSettings(String settingsName) {
    var resourceSettings = localFileProvider.read(getIndexSettingsPath(settingsName));
    try {
      return jsonConverter.asJsonTree(resourceSettings);
    } catch (SerializationException e) {
      throw new ResourceDescriptionException(String.format(
        "Failed to load resource index settings [resourceName: %s], msg: %s", settingsName, e.getMessage()));
    }
  }

  private static String getIndexSettingsPath(String resourcePath) {
    return "elasticsearch/index/" + resourcePath + ".json";
  }
}
