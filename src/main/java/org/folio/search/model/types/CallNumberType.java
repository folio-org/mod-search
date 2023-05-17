package org.folio.search.model.types;

import java.util.Optional;
import lombok.Getter;

@Getter
public enum CallNumberType {

  LC("95467209-6d7b-468b-94df-0f5d7ad2747d", CallNumberTypeSource.SYSTEM, 1),
  DEWEY("03dd64d0-5626-4ecd-8ece-4531e0069f35", CallNumberTypeSource.SYSTEM, 2),
  NLM("054d460d-d6b9-4469-9e37-7a78a2266655", CallNumberTypeSource.SYSTEM, 3),
  SUDOC("fc388041-6cd0-4806-8a74-ebe3b9ab4c6e", CallNumberTypeSource.SYSTEM, 4),
  OTHER("6caca63e-5651-4db6-9247-3205156e9699", CallNumberTypeSource.SYSTEM, 5),
  LOCAL("*", CallNumberTypeSource.LOCAL, 6);

  private final String id;
  private final CallNumberTypeSource source;
  /**
   * Number that is used for browsing by typed call number.
   */
  private final int number;

  CallNumberType(String id, CallNumberTypeSource source, int number) {
    this.id = id;
    this.source = source;
    this.number = number;
  }

  public static Optional<CallNumberType> fromName(String name) {
    for (CallNumberType value : values()) {
      if (value.name().equalsIgnoreCase(name)) {
        return Optional.of(value);
      }
    }
    return Optional.empty();
  }

  public static Optional<CallNumberType> fromId(String id) {
    for (CallNumberType value : values()) {
      if (value.getId().equalsIgnoreCase(id)) {
        return Optional.of(value);
      }
    }
    return Optional.empty();
  }

}
