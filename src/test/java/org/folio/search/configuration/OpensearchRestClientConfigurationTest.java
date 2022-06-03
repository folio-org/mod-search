package org.folio.search.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.Stream;
import org.folio.search.configuration.opensearch.RestClientBuilderCustomizer;
import org.folio.search.configuration.properties.OpensearchProperties;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.client.RestHighLevelClient;
import org.springframework.beans.factory.ObjectProvider;

@UnitTest
@ExtendWith(MockitoExtension.class)
class OpensearchRestClientConfigurationTest {

  @InjectMocks
  private OpensearchRestClientConfiguration restClientConfiguration;
  @Mock
  private ObjectProvider<RestClientBuilderCustomizer> customizers;
  @Mock
  private OpensearchProperties properties;
  @Mock
  private RestClientBuilder restClientBuilder;

  @ValueSource(strings = {
    "http://elasticsearch:9200",
    "http://elasticsearch:spock123@enterprisecom",
    "\\elasticsearch"
  })
  @ParameterizedTest
  void opensearchRestClientBuilder(String uri) {
    when(properties.getPathPrefix()).thenReturn(null);
    when(properties.getUris()).thenReturn(List.of(uri));
    var builder = restClientConfiguration.opensearchRestClientBuilder(customizers, properties);
    assertThat(builder).isNotNull();
  }

  @Test
  void opensearchRestHighLevelClient() {
    when(restClientBuilder.build()).thenReturn(mock(RestClient.class));
    var restHighLevelClient = restClientConfiguration.opensearchRestHighLevelClient(restClientBuilder);
    assertThat(restHighLevelClient).isNotNull();
  }

  @Test
  void opensearchRestHighLevelClient_withUserInfo() {
    when(properties.getPathPrefix()).thenReturn(null);
    when(properties.getUris()).thenReturn(List.of("http://elasticsearch:spock123@enterprisecom"));
    when(properties.getUsername()).thenReturn("username");
    when(properties.getPassword()).thenReturn("password");
    when(customizers.orderedStream())
      .thenReturn(Stream.of(restClientConfiguration.defaultRestClientBuilderCustomizer(properties)))
      .thenReturn(Stream.of(restClientConfiguration.defaultRestClientBuilderCustomizer(properties)));
    var builder = restClientConfiguration.opensearchRestClientBuilder(customizers, properties);
    var restHighLevelClient = restClientConfiguration.opensearchRestHighLevelClient(builder);
    assertThat(restHighLevelClient).isNotNull();
  }

  @Test
  void opensearchRestClient() {
    var restHighLevelClient = mock(RestHighLevelClient.class);
    when(restHighLevelClient.getLowLevelClient()).thenReturn(mock(RestClient.class));
    var restClient = restClientConfiguration.opensearchRestClient(restHighLevelClient);
    assertThat(restClient).isNotNull();
  }
}
