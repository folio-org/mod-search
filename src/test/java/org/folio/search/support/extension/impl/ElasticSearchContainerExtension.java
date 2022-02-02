package org.folio.search.support.extension.impl;

import java.nio.file.Path;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;

public class ElasticSearchContainerExtension implements BeforeAllCallback, AfterAllCallback {

  private static final String SPRING_PROPERTY_NAME = "spring.elasticsearch.rest.uris";
  private static final String ES_IMAGE_NAME = "opendistro-elasticsearch-it:1.13.2";
  private static final Path ES_DOCKERFILE_PATH = Path.of("docker/elasticsearch/Dockerfile");
  private static final GenericContainer<?> CONTAINER = createContainer();

  @Override
  public void beforeAll(ExtensionContext context) {
    if (!CONTAINER.isRunning()) {
      CONTAINER.start();
    }

    System.setProperty(SPRING_PROPERTY_NAME, getElasticSearchUrl());
  }

  @Override
  public void afterAll(ExtensionContext context) {
    System.clearProperty(SPRING_PROPERTY_NAME);
  }

  private String getElasticSearchUrl() {
    return "http://" + CONTAINER.getHost() + ":" + CONTAINER.getMappedPort(9200);
  }

  private static GenericContainer<?> createContainer() {
    return new GenericContainer<>(new ImageFromDockerfile(ES_IMAGE_NAME, false)
      .withDockerfile(ES_DOCKERFILE_PATH))
      .withEnv("discovery.type", "single-node")
      .withEnv("opendistro_security.disabled", "true")
      .withExposedPorts(9200);
  }
}
