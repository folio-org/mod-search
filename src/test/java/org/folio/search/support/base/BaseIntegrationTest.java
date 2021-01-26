package org.folio.search.support.base;

import static java.lang.String.format;
import static org.folio.search.sample.SampleInstances.getSemanticWeb;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.SearchUtils.X_OKAPI_TENANT_HEADER;
import static org.folio.search.utils.TestConstants.INVENTORY_INSTANCE_TOPIC;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.asJsonString;
import static org.folio.search.utils.TestUtils.eventBody;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.testcontainers.utility.DockerImageName.parse;

import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.folio.search.domain.dto.IndexRequestBody;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public abstract class BaseIntegrationTest {
  private static final DockerImageName KAFKA_IMAGE = parse("confluentinc/cp-kafka:5.5.3");
  private static final String ES_IMAGE_NAME = "test-container-embedded-es:7.10.1";
  private static final Path ES_DOCKERFILE_PATH = Path.of("docker/elasticsearch/Dockerfile");

  @Container
  private static final KafkaContainer KAFKA_CONTAINER = new KafkaContainer(KAFKA_IMAGE);
  @Container
  private static final GenericContainer<?> ES_CONTAINER = new GenericContainer<>(
    new ImageFromDockerfile(ES_IMAGE_NAME, true).withDockerfile(ES_DOCKERFILE_PATH))
    .withEnv("discovery.type", "single-node")
    .withExposedPorts(9200);

  @Autowired
  protected MockMvc mockMvc;

  @DynamicPropertySource
  @SuppressWarnings("unused")
  static void externalSystemsUris(DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers", KAFKA_CONTAINER::getBootstrapServers);
    registry.add("spring.elasticsearch.rest.uris",
      () -> format("http://%s:%s", ES_CONTAINER.getHost(), ES_CONTAINER.getMappedPort(9200)));
  }

  @BeforeAll
  static void createIndexAndUploadInstances(@Autowired MockMvc mockMvc,
                                            @Autowired KafkaTemplate<String, Object> kafkaTemplate) throws Exception {

    mockMvc.perform(post("/_/tenant")
      .content(asJsonString(new TenantAttributes().moduleTo("mod-search-1.0.0")))
      .headers(defaultHeaders())
      .contentType(APPLICATION_JSON))
      .andExpect(status().isOk());

    mockMvc.perform(post("/search/index/indices")
      .content(asJsonString(new IndexRequestBody().resourceName(INSTANCE_RESOURCE)))
      .headers(defaultHeaders())
      .contentType(APPLICATION_JSON))
      .andExpect(status().isOk());

    kafkaTemplate.send(INVENTORY_INSTANCE_TOPIC, getSemanticWeb().getId(),
      eventBody(INSTANCE_RESOURCE, getSemanticWeb()));
  }

  public static HttpHeaders defaultHeaders() {
    final HttpHeaders httpHeaders = new HttpHeaders();

    httpHeaders.put(X_OKAPI_TENANT_HEADER, List.of(TENANT_ID));

    return httpHeaders;
  }

  @SneakyThrows
  public ResultActions attemptPost(String uri, Object body) {
    return mockMvc.perform(post(uri)
      .content(asJsonString(body))
      .headers(defaultHeaders())
      .contentType(APPLICATION_JSON));
  }

  @SneakyThrows
  public ResultActions doPost(String uri, Object body) {
    return attemptPost(uri, body)
      .andExpect(status().isOk());
  }

  @SneakyThrows
  public ResultActions doGet(String uri, Object... args) {
    return mockMvc.perform(get(uri, args)
      .headers(defaultHeaders()))
      .andExpect(status().isOk());
  }

  @SneakyThrows
  public ResultActions doDelete(String uri, Object... args) {
    return mockMvc.perform(delete(uri, args)
      .headers(defaultHeaders()))
      .andExpect(status().isNoContent());
  }
}
