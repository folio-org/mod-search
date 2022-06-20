package org.folio.search.utils;

import static org.folio.search.configuration.properties.FolioEnvironment.getFolioEnvName;

import lombok.experimental.UtilityClass;

@UtilityClass
public class KafkaUtils {

  /**
   * Returns topic name in the format - `{env}.{tenant}.{topic-name}`
   *
   * @param initialName initial topic name as {@link String}
   * @param tenantId    tenant id as {@link String}
   * @return topic name as {@link String} object
   */
  public static String getTenantTopicName(String initialName, String tenantId) {
    return String.format("%s.%s.%s", getFolioEnvName(), tenantId, initialName);
  }
}
