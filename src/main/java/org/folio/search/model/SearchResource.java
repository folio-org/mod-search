package org.folio.search.model;

public enum SearchResource {
  INSTANCE("instance");

  private final String name;

  SearchResource(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }
}
