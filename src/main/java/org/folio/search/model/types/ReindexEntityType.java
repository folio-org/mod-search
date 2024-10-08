package org.folio.search.model.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;

@Getter
public enum ReindexEntityType {

  INSTANCE("instance", true, true),
  SUBJECT("subject", false, true),
  CONTRIBUTOR("contributor", false, true),
  CLASSIFICATION("classification", false, true),
  ITEM("item", true, false),
  HOLDINGS("holdings", true, false);

  private final String type;
  private final boolean supportsMerge;
  private final boolean supportsUpload;

  ReindexEntityType(String type, boolean supportsMerge, boolean supportsUpload) {
    this.type = type;
    this.supportsMerge = supportsMerge;
    this.supportsUpload = supportsUpload;
  }

  @JsonCreator
  public static ReindexEntityType fromValue(String value) {
    for (ReindexEntityType b : ReindexEntityType.values()) {
      if (b.type.equalsIgnoreCase(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }

  public static List<ReindexEntityType> supportMergeTypes() {
    return Arrays.stream(values()).filter(ReindexEntityType::isSupportsMerge).toList();
  }

  public static List<ReindexEntityType> supportUploadTypes() {
    return Arrays.stream(values()).filter(ReindexEntityType::isSupportsUpload).toList();
  }
}
