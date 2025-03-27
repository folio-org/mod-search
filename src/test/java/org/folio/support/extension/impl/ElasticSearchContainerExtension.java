package org.folio.support.extension.impl;

import java.nio.file.Path;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;

@Log4j2
public class ElasticSearchContainerExtension implements BeforeAllCallback, AfterAllCallback {

  private static final String SPRING_PROPERTY_NAME = "spring.opensearch.uris";
  private static final String IMAGE_NAME = "dev.folio/searchengine";
  private static final String DEFAULT_DOCKERFILE = "docker/opensearch/Dockerfile";
  private static final GenericContainer<?> CONTAINER = createContainer();

  @Override
  public void beforeAll(ExtensionContext context) {
    if (!CONTAINER.isRunning()) {
      CONTAINER.start();
    }

    System.setProperty(SPRING_PROPERTY_NAME, getSearchUrl());
  }

  @Override
  public void afterAll(ExtensionContext context) {
    System.clearProperty(SPRING_PROPERTY_NAME);
  }

  private String getSearchUrl() {
    return "http://" + CONTAINER.getHost() + ":" + CONTAINER.getMappedPort(9200);
  }

  private static GenericContainer<?> createContainer() {
    var dockerfile = System.getenv().getOrDefault("SEARCH_ENGINE_DOCKERFILE", DEFAULT_DOCKERFILE);
    log.info("search engine dockerfile: {}", dockerfile);
    var container = new GenericContainer<>(new ImageFromDockerfile(IMAGE_NAME, false)
      .withDockerfile(Path.of(dockerfile)))
      .withEnv("discovery.type", "single-node")
      .withExposedPorts(9200);
    if (dockerfile.contains("opensearch")) {
      container.withEnv("DISABLE_SECURITY_PLUGIN", "true");
    } else {  // elasticsearch
      container.withEnv("xpack.security.enabled", "false");
    }
    return container;
  }
}
