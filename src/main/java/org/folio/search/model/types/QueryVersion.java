package org.folio.search.model.types;

import lombok.Getter;
import org.folio.search.model.service.QueryResolution;

@Getter
public enum QueryVersion {

  V1("1", "instance", ResourceType.INSTANCE, QueryResolution.PathType.LEGACY),
  V2("2", "instance_search", ResourceType.INSTANCE_SEARCH, QueryResolution.PathType.FLAT);

  private final String value;
  private final String indexPrefix;
  private final ResourceType resourceType;
  private final QueryResolution.PathType pathType;

  QueryVersion(String value, String indexPrefix, ResourceType resourceType, QueryResolution.PathType pathType) {
    this.value = value;
    this.indexPrefix = indexPrefix;
    this.resourceType = resourceType;
    this.pathType = pathType;
  }

  public static QueryVersion fromString(String version) {
    for (QueryVersion v : values()) {
      if (v.value.equals(version) || v.name().equalsIgnoreCase(version)) {
        return v;
      }
    }
    throw new IllegalArgumentException("Unknown query version: " + version);
  }

  public static QueryVersion getDefault() {
    return V1;
  }
}
