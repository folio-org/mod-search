package org.folio.search.model.entity;

import static org.apache.commons.lang3.StringUtils.truncate;

import java.util.Objects;
import lombok.Getter;
import org.folio.search.utils.ShaUtils;
import org.jetbrains.annotations.NotNull;

@Getter
public class CallNumberEntity implements Comparable<CallNumberEntity> {

  public static final int CALL_NUMBER_MAX_LENGTH = 50;
  private static final int CALL_NUMBER_PREFIX_MAX_LENGTH = 20;
  private static final int CALL_NUMBER_SUFFIX_MAX_LENGTH = 25;
  private static final int CALL_NUMBER_TYPE_MAX_LENGTH = 40;

  private final String id;
  private final String callNumber;
  private final String callNumberPrefix;
  private final String callNumberSuffix;
  private final String callNumberTypeId;

  CallNumberEntity(String id, String callNumber, String callNumberPrefix, String callNumberSuffix,
                   String callNumberTypeId) {
    this.id = id;
    this.callNumber = callNumber;
    this.callNumberPrefix = callNumberPrefix;
    this.callNumberSuffix = callNumberSuffix;
    this.callNumberTypeId = callNumberTypeId;
  }

  public static CallNumberEntityBuilder builder() {
    return new CallNumberEntityBuilder();
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof CallNumberEntity that)) {
      return false;
    }
    return Objects.equals(id, that.id);
  }

  @Override
  public int compareTo(@NotNull CallNumberEntity o) {
    return id.compareTo(o.id);
  }

  public static class CallNumberEntityBuilder {
    private String id;
    private String callNumber;
    private String callNumberPrefix;
    private String callNumberSuffix;
    private String callNumberTypeId;

    CallNumberEntityBuilder() { }

    public CallNumberEntityBuilder id(String id) {
      this.id = id;
      return this;
    }

    public CallNumberEntityBuilder callNumber(String callNumber) {
      this.callNumber = truncate(callNumber, CALL_NUMBER_MAX_LENGTH);
      return this;
    }

    public CallNumberEntityBuilder callNumberPrefix(String callNumberPrefix) {
      this.callNumberPrefix = truncate(callNumberPrefix, CALL_NUMBER_PREFIX_MAX_LENGTH);
      return this;
    }

    public CallNumberEntityBuilder callNumberSuffix(String callNumberSuffix) {
      this.callNumberSuffix = truncate(callNumberSuffix, CALL_NUMBER_SUFFIX_MAX_LENGTH);
      return this;
    }

    public CallNumberEntityBuilder callNumberTypeId(String callNumberTypeId) {
      this.callNumberTypeId = truncate(callNumberTypeId, CALL_NUMBER_TYPE_MAX_LENGTH);
      return this;
    }

    public CallNumberEntity build() {
      if (id == null) {
        this.id = ShaUtils.sha(callNumber, callNumberPrefix, callNumberSuffix, callNumberTypeId);
      }
      return new CallNumberEntity(this.id, this.callNumber, this.callNumberPrefix, this.callNumberSuffix,
        this.callNumberTypeId);
    }
  }
}
