package org.folio.search.support.base;

import static org.folio.dbschema.ObjectMapperTool.getMapper;
import static org.folio.search.sample.SampleInstances.getSemanticWeb;
import static org.folio.search.utils.TestUtils.asJsonString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.util.List;

import org.folio.okapi.common.XOkapiHeaders;
import org.folio.search.model.ResourceEventBody;
import org.folio.search.model.rest.request.IndexRequestBody;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.node.ObjectNode;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public abstract class BaseIntegrationTest {
  private static final String TENANT = "test";
  private static final String INSTANCE_RESOURCE = "instance";

  @Autowired
  protected MockMvc mockMvc;

  @Container
  private final static GenericContainer<?> esContainer = new GenericContainer<>(
    new ImageFromDockerfile("test-container-embedded-es:7.10.1", false)
      .withDockerfile(Path.of("docker/elasticsearch/Dockerfile")))
    .withEnv("discovery.type", "single-node")
    .withExposedPorts(9200);

  @DynamicPropertySource
  @SuppressWarnings("unused")
  private static void esUrisProperty(DynamicPropertyRegistry registry) {
    registry.add("spring.elasticsearch.rest.uris",
      () -> "http://localhost:" + esContainer.getMappedPort(9200));
  }

  @BeforeAll
  @SuppressWarnings("unused")
  private static void createIndexAndUploadInstances(@Autowired MockMvc mockMvc) throws Exception {
    mockMvc.perform(post("/search/index/indices")
      .content(asJsonString(IndexRequestBody.of(INSTANCE_RESOURCE)))
      .headers(defaultHeaders())
      .contentType(APPLICATION_JSON))
      .andExpect(status().isOk());

    mockMvc.perform(post("/search/index/resources")
      .content(asJsonString(List.of(getInstance())))
      .headers(defaultHeaders())
      .contentType(APPLICATION_JSON))
      .andExpect(status().isCreated());
  }

  private static ResourceEventBody getInstance() {
    final ObjectNode instanceAsObjectNode = getMapper()
      .convertValue(getSemanticWeb(), ObjectNode.class);

    return ResourceEventBody
      .of("CREATE", TENANT, INSTANCE_RESOURCE, instanceAsObjectNode);
  }

  public static HttpHeaders defaultHeaders() {
    final HttpHeaders httpHeaders = new HttpHeaders();

    httpHeaders.put(XOkapiHeaders.TENANT, List.of(TENANT));

    return httpHeaders;
  }
}
