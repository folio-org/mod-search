package org.folio.search.model.index;

import lombok.Getter;

@Getter
public enum AuthRefType {

  AUTHORIZED("Authorized"),
  REFERENCE("Reference"),
  AUTH_REF("Auth/Ref");

  private final String typeValue;

  AuthRefType(String typeValue) {
    this.typeValue = typeValue;
  }
}
