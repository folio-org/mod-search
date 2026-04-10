package org.folio.search.utils;

import java.util.Map;
import lombok.experimental.UtilityClass;
import org.folio.search.model.types.ResourceType;

@UtilityClass
public class V2BrowseIndexNameResolver {

  private static final Map<ResourceType, ResourceType> V1_TO_V2_BROWSE = Map.of(
    ResourceType.INSTANCE_CONTRIBUTOR, ResourceType.V2_CONTRIBUTOR,
    ResourceType.INSTANCE_SUBJECT, ResourceType.V2_SUBJECT,
    ResourceType.INSTANCE_CLASSIFICATION, ResourceType.V2_CLASSIFICATION,
    ResourceType.INSTANCE_CALL_NUMBER, ResourceType.V2_CALL_NUMBER
  );

  public static ResourceType resolveV2BrowseType(ResourceType v1BrowseType) {
    var v2Type = V1_TO_V2_BROWSE.get(v1BrowseType);
    if (v2Type == null) {
      throw new IllegalArgumentException("No V2 browse mapping for resource type: " + v1BrowseType);
    }
    return v2Type;
  }

  public static boolean isV1BrowseType(ResourceType resourceType) {
    return V1_TO_V2_BROWSE.containsKey(resourceType);
  }
}
