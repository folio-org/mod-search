package org.folio.search.support.base;

import static java.lang.String.format;
import static org.awaitility.Awaitility.await;
import static org.folio.search.sample.SampleInstances.getSemanticWeb;
import static org.folio.search.support.base.ApiEndpoints.searchInstancesByQuery;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.SearchUtils.X_OKAPI_TENANT_HEADER;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.asJsonString;
import static org.folio.search.utils.TestUtils.eventBody;
import static org.hamcrest.Matchers.is;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.testcontainers.utility.DockerImageName.parse;

import java.nio.file.Path;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.awaitility.Duration;
import org.folio.search.domain.dto.IndexRequestBody;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;

@Log4j2
@SpringBootTest
@AutoConfigureMockMvc
public abstract class BaseIntegrationTest {

  private static final DockerImageName KAFKA_IMAGE = parse("confluentinc/cp-kafka:5.5.3");
  private static final String ES_IMAGE_NAME = "test-container-embedded-es:7.10.1";
  private static final Path ES_DOCKERFILE_PATH = Path.of("docker/elasticsearch/Dockerfile");

  private static final KafkaContainer KAFKA_CONTAINER = createAndStartKafka();
  private static final GenericContainer<?> ES_CONTAINER = createAndStartElasticsearch();

  private static boolean alreadyInitialized = false;

  @Autowired protected MockMvc mockMvc;

  @DynamicPropertySource
  @SuppressWarnings("unused")
  static void externalSystemsUris(DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers", KAFKA_CONTAINER::getBootstrapServers);
    registry.add("spring.elasticsearch.rest.uris",
      () -> format("http://%s:%s", ES_CONTAINER.getHost(), ES_CONTAINER.getMappedPort(9200)));
  }

  @BeforeAll
  static void doFirstInitialization(@Autowired MockMvc mockMvc,
    @Autowired KafkaTemplate<String, Object> kafkaTemplate) throws Exception {

    if (!alreadyInitialized) {
      mockMvc.perform(post("/search/index/indices")
        .content(asJsonString(new IndexRequestBody().resourceName(INSTANCE_RESOURCE)))
        .headers(defaultHeaders())
        .contentType(APPLICATION_JSON))
        .andExpect(status().isOk());

      kafkaTemplate.send("inventory.instance", getSemanticWeb().getId(),
        eventBody(INSTANCE_RESOURCE, getSemanticWeb()));

      checkThatElasticsearchAcceptResourcesFromKafka(mockMvc);

      alreadyInitialized = true;
    }
  }

  public static HttpHeaders defaultHeaders() {
    final HttpHeaders httpHeaders = new HttpHeaders();

    httpHeaders.put(X_OKAPI_TENANT_HEADER, List.of(TENANT_ID));

    return httpHeaders;
  }

  private static void checkThatElasticsearchAcceptResourcesFromKafka(MockMvc mockMvc) {
    await().atMost(Duration.ONE_MINUTE).untilAsserted(() ->
      mockMvc.perform(get(searchInstancesByQuery("id={value}"), getSemanticWeb().getId())
        .headers(defaultHeaders()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("totalRecords", is(1)))
        .andExpect(jsonPath("instances[0].id", is(getSemanticWeb().getId()))));
  }

  private static KafkaContainer createAndStartKafka() {
    final KafkaContainer kafkaContainer = new KafkaContainer(KAFKA_IMAGE).withReuse(true);

    kafkaContainer.start();

    Runtime.getRuntime().addShutdownHook(new Thread(kafkaContainer::stop));

    return kafkaContainer;
  }

  private static GenericContainer<?> createAndStartElasticsearch() {
    final GenericContainer<?> esContainer = new GenericContainer<>(
      new ImageFromDockerfile(ES_IMAGE_NAME, false).withDockerfile(ES_DOCKERFILE_PATH))
      .withEnv("discovery.type", "single-node")
      .withExposedPorts(9200)
      // Reuse container between tests and control their lifecycle manually
      .withReuse(true);

    esContainer.start();

    Runtime.getRuntime().addShutdownHook(new Thread(esContainer::stop));

    return esContainer;
  }
}
