package org.folio.search.model.entity;

import static org.apache.commons.lang3.StringUtils.truncate;

import java.util.Objects;
import lombok.Getter;
import org.folio.search.utils.ShaUtils;
import org.jetbrains.annotations.NotNull;

@Getter
public class CallNumberEntity implements Comparable<CallNumberEntity> {

  private static final int CALL_NUMBER_MAX_LENGTH = 50;
  private static final int CALL_NUMBER_PREFIX_MAX_LENGTH = 20;
  private static final int CALL_NUMBER_SUFFIX_MAX_LENGTH = 25;
  private static final int CALL_NUMBER_TYPE_MAX_LENGTH = 40;
  private static final int VOLUME_MAX_LENGTH = 50;
  private static final int ENUMERATION_MAX_LENGTH = 50;
  private static final int CHRONOLOGY_MAX_LENGTH = 50;
  private static final int COPY_NUMBER_MAX_LENGTH = 10;

  private String id;
  private String callNumber;
  private String callNumberPrefix;
  private String callNumberSuffix;
  private String callNumberTypeId;
  private String volume;
  private String enumeration;
  private String chronology;
  private String copyNumber;

  CallNumberEntity(String id, String callNumber, String callNumberPrefix, String callNumberSuffix,
                   String callNumberTypeId, String volume, String enumeration, String chronology, String copyNumber) {
    this.id = id;
    this.callNumber = callNumber;
    this.callNumberPrefix = callNumberPrefix;
    this.callNumberSuffix = callNumberSuffix;
    this.callNumberTypeId = callNumberTypeId;
    this.volume = volume;
    this.enumeration = enumeration;
    this.chronology = chronology;
    this.copyNumber = copyNumber;
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
    private String volume;
    private String enumeration;
    private String chronology;
    private String copyNumber;

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

    public CallNumberEntityBuilder volume(String volume) {
      this.volume = truncate(volume, VOLUME_MAX_LENGTH);
      return this;
    }

    public CallNumberEntityBuilder enumeration(String enumeration) {
      this.enumeration = truncate(enumeration, ENUMERATION_MAX_LENGTH);
      return this;
    }

    public CallNumberEntityBuilder chronology(String chronology) {
      this.chronology = truncate(chronology, CHRONOLOGY_MAX_LENGTH);
      return this;
    }

    public CallNumberEntityBuilder copyNumber(String copyNumber) {
      this.copyNumber = truncate(copyNumber, COPY_NUMBER_MAX_LENGTH);
      return this;
    }

    public CallNumberEntity build() {
      if (id == null) {
        this.id = ShaUtils.sha(callNumber, callNumberPrefix, callNumberSuffix, callNumberTypeId,
          volume, enumeration, chronology, copyNumber);
      }
      return new CallNumberEntity(this.id, this.callNumber, this.callNumberPrefix, this.callNumberSuffix,
        this.callNumberTypeId, this.volume, this.enumeration, this.chronology, this.copyNumber);
    }

  }
}
