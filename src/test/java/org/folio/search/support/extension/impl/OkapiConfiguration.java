package org.folio.search.support.extension.impl;

import com.github.tomakehurst.wiremock.WireMockServer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class OkapiConfiguration {
  private final WireMockServer wireMockServer;
  private final int port;

  public String getOkapiUrl() {
    return "http://localhost:" + port;
  }
}
