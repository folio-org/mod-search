package org.folio.search.model.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.List;
import lombok.Data;
import lombok.Getter;
import org.folio.search.model.types.ReindexEntityType;

@Data
public class ReindexRecordsEvent {

  private List<Object> records;
  private ReindexRecordType recordType;
  private String tenant;
  private String rangeId;

  @Getter
  public enum ReindexRecordType {

    INSTANCE("instance", ReindexEntityType.INSTANCE),
    ITEM("item", ReindexEntityType.ITEM),
    HOLDINGS("holdings", ReindexEntityType.HOLDINGS);

    private final String value;
    private final ReindexEntityType entityType;

    ReindexRecordType(String value, ReindexEntityType entityType) {
      this.value = value;
      this.entityType = entityType;
    }

    @JsonCreator
    public static ReindexRecordType fromValue(String value) {
      for (ReindexRecordType b : ReindexRecordType.values()) {
        if (b.value.equalsIgnoreCase(value)) {
          return b;
        }
      }
      throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }
  }
}
