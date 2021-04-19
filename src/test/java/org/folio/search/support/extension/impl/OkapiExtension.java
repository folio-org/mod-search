package org.folio.search.support.extension.impl;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.util.Optional.ofNullable;
import static org.springframework.util.ReflectionUtils.findField;
import static org.springframework.util.ReflectionUtils.makeAccessible;
import static org.springframework.util.ReflectionUtils.setField;
import static org.springframework.util.SocketUtils.findAvailableTcpPort;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.folio.search.support.api.InventoryViewResponseBuilder;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class OkapiExtension implements BeforeAllCallback, AfterAllCallback {
  private static final int OKAPI_PORT = findAvailableTcpPort();
  private static final WireMockServer WIRE_MOCK = new WireMockServer(
    wireMockConfig().port(OKAPI_PORT).extensions(new InventoryViewResponseBuilder()));

  @Override
  public void afterAll(ExtensionContext extensionContext) {
    WIRE_MOCK.stop();
    System.clearProperty("okapi.url");
  }

  @Override
  public void beforeAll(ExtensionContext extensionContext) {
    WIRE_MOCK.start();

    var okapiConfiguration = new OkapiConfiguration(WIRE_MOCK, OKAPI_PORT);
    System.setProperty("okapi.url", okapiConfiguration.getOkapiUrl());

    ofNullable(findField(extensionContext.getRequiredTestClass(), null, OkapiConfiguration.class))
      .ifPresent(field -> {
        makeAccessible(field);
        setField(field, null, okapiConfiguration);
      });
  }
}
