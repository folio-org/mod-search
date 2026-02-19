package org.folio.support.extension.impl;

import java.nio.file.Path;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.opensearch.testcontainers.OpenSearchContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;

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

  private static GenericContainer<?> buildSearchContainer(String dockerfile, String imageTag) {
    if (dockerfile.contains("opensearch")) {
      return new OpenSearchContainer<>(
        DockerImageName.parse(imageTag)
          .asCompatibleSubstituteFor("opensearchproject/opensearch"));
    } else {
      return new ElasticsearchContainer(
        DockerImageName.parse(imageTag)
          .asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch"))
        .withEnv("xpack.security.enabled", "false");
    }
  }

  private String getSearchUrl() {
    return "http://" + CONTAINER.getHost() + ":" + CONTAINER.getMappedPort(9200);
  }

  private static GenericContainer<?> createContainer() {
    var dockerfile = System.getenv().getOrDefault("SEARCH_ENGINE_DOCKERFILE", DEFAULT_DOCKERFILE);
    log.info("search engine dockerfile: {}", dockerfile);

    Path dockerfilePath = Path.of(dockerfile);
    if (!dockerfilePath.toFile().exists()) {
      throw new RuntimeException("Dockerfile not found at: " + dockerfile);
    }

    String imageTag = buildImage(dockerfilePath);
    return buildSearchContainer(dockerfile, imageTag);
  }

  private static String buildImage(Path dockerfilePath) {
    new ImageFromDockerfile(IMAGE_NAME, false)
      .withDockerfile(dockerfilePath)
      .get();
    return IMAGE_NAME + ":latest";
  }
}

