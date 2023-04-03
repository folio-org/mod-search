package org.folio.search.model.index;

import lombok.Getter;

public enum AuthRefType {

  AUTHORIZED("Authorized"),
  REFERENCE("Reference"),
  AUTH_REF("Auth/Ref");

  @Getter
  private final String typeValue;

  AuthRefType(String typeValue) {
    this.typeValue = typeValue;
  }
}
