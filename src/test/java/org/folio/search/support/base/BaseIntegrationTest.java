package org.folio.search.support.base;

import static java.lang.String.format;
import static org.awaitility.Awaitility.await;
import static org.folio.search.sample.SampleInstances.getSemanticWeb;
import static org.folio.search.support.base.ApiEndpoints.searchInstancesByQuery;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.SearchUtils.X_OKAPI_TENANT_HEADER;
import static org.folio.search.utils.SearchUtils.getElasticsearchIndexName;
import static org.folio.search.utils.TestConstants.INVENTORY_INSTANCE_TOPIC;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.asJsonString;
import static org.folio.search.utils.TestUtils.eventBody;
import static org.hamcrest.Matchers.is;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.util.SocketUtils.findAvailableTcpPort;
import static org.testcontainers.utility.DockerImageName.parse;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.awaitility.Duration;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.folio.search.domain.dto.Instance;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;

@Log4j2
@SpringBootTest
@AutoConfigureMockMvc
public abstract class BaseIntegrationTest {

  protected static final WireMockServer WIRE_MOCK = new WireMockServer(findAvailableTcpPort());
  private static final DockerImageName KAFKA_IMAGE = parse("confluentinc/cp-kafka:5.5.3");
  private static final String ES_IMAGE_NAME = "test-container-embedded-es:7.10.1";
  private static final Path ES_DOCKERFILE_PATH = Path.of("docker/elasticsearch/Dockerfile");

  private static final KafkaContainer KAFKA_CONTAINER = createAndStartKafka();
  private static final GenericContainer<?> ES_CONTAINER = createAndStartElasticsearch();

  @Autowired protected MockMvc mockMvc;

  @DynamicPropertySource
  @SuppressWarnings("unused")
  static void externalSystemsUris(DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers", KAFKA_CONTAINER::getBootstrapServers);
    registry.add("spring.elasticsearch.rest.uris",
      () -> format("http://%s:%s", ES_CONTAINER.getHost(), ES_CONTAINER.getMappedPort(9200)));
  }

  @BeforeAll
  static void setUpDefaultTenant(@Autowired MockMvc mockMvc,
    @Autowired KafkaTemplate<String, Object> kafkaTemplate) {

    WIRE_MOCK.start();

    setUpTenant(TENANT_ID, mockMvc, kafkaTemplate, getSemanticWeb());
  }

  @AfterAll
  static void removeDefaultTenant(@Autowired RestHighLevelClient highLevelClient,
    @Autowired JdbcTemplate jdbcTemplate) {

    removeTenant(highLevelClient, jdbcTemplate, TENANT_ID);

    WIRE_MOCK.stop();
  }

  public static HttpHeaders defaultHeaders() {
    return defaultHeaders(TENANT_ID);
  }

  public static HttpHeaders defaultHeaders(String tenant) {
    final HttpHeaders httpHeaders = new HttpHeaders();

    httpHeaders.setContentType(APPLICATION_JSON);
    httpHeaders.put(X_OKAPI_TENANT_HEADER, List.of(tenant));
    httpHeaders.add(XOkapiHeaders.URL, WIRE_MOCK.baseUrl());

    return httpHeaders;
  }

  private static void checkThatElasticsearchAcceptResourcesFromKafka(
    String tenant, MockMvc mockMvc, String id) {

    await().atMost(Duration.ONE_MINUTE).untilAsserted(() ->
      mockMvc.perform(get(searchInstancesByQuery("id={value}"), id)
        .headers(defaultHeaders(tenant)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("totalRecords", is(1)))
        .andExpect(jsonPath("instances[0].id", is(id))));
  }

  private static KafkaContainer createAndStartKafka() {
    final KafkaContainer kafkaContainer = new KafkaContainer(KAFKA_IMAGE)
      .withReuse(true)
      .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");

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
    return doGet(mockMvc, uri, args);
  }

  @SneakyThrows
  public static ResultActions doGet(MockMvc mockMvc, String uri, Object... args) {
    return mockMvc.perform(get(uri, args)
      .headers(defaultHeaders()))
      .andExpect(status().isOk());
  }

  @SneakyThrows
  public ResultActions doDelete(String uri, Object... args) {
    return doDelete(mockMvc, uri, args);
  }

  @SneakyThrows
  public static ResultActions doDelete(MockMvc mockMvc, String uri, Object... args) {
    return mockMvc.perform(delete(uri, args)
      .headers(defaultHeaders()))
      .andExpect(status().isNoContent());
  }

  @SneakyThrows
  protected static void setUpTenant(String tenantName, MockMvc mockMvc,
    KafkaTemplate<String, Object> kafkaTemplate, Instance ... instances) {

    mockMvc.perform(post("/_/tenant")
      .content(asJsonString(new TenantAttributes().moduleTo("mod-search-1.0.0")))
      .headers(defaultHeaders(tenantName))
      .contentType(APPLICATION_JSON))
      .andExpect(status().isOk());

    for (Instance instance : instances) {
      kafkaTemplate.send(INVENTORY_INSTANCE_TOPIC, instance.getId(),
        eventBody(INSTANCE_RESOURCE, instance).tenant(tenantName));
    }

    if (instances.length > 0) {
      checkThatElasticsearchAcceptResourcesFromKafka(tenantName, mockMvc,
        instances[instances.length - 1].getId());
    }
  }

  @SneakyThrows
  protected static void removeTenant(RestHighLevelClient highLevelClient,
    JdbcTemplate jdbcTemplate, String tenant) {

    log.info("Removing elasticsearch index...");
    highLevelClient.indices().delete(new DeleteIndexRequest()
      .indices(getElasticsearchIndexName(INSTANCE_RESOURCE, tenant)), RequestOptions.DEFAULT);

    log.info("Destroying schema...");
    jdbcTemplate.execute(format("DROP SCHEMA %s_mod_search CASCADE", tenant));
  }
}
