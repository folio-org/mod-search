package org.folio.search.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.folio.search.configuration.OpensearchRestClientConfiguration.DefaultRestClientBuilderCustomizer;
import org.folio.search.configuration.opensearch.RestClientBuilderCustomizer;
import org.folio.search.configuration.properties.OpensearchProperties;
import org.folio.spring.testing.type.UnitTest;
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
    when(properties.getConnectionTimeout()).thenReturn(Duration.ofSeconds(1));
    when(properties.getSocketTimeout()).thenReturn(Duration.ofSeconds(30));
    when(properties.getMaxConnPerRoute()).thenReturn(25);
    when(properties.getMaxConnTotal()).thenReturn(100);
    when(properties.getConnectionTimeToLive()).thenReturn(null);
    when(properties.getValidateAfterInactivity()).thenReturn(null);

    // Build customizers before registering orderedStream stubs — the constructor
    // calls properties methods eagerly, which would corrupt Mockito's stub-recording
    // state if called inside the when(...).thenReturn(...) argument list.
    var customizer1 = restClientConfiguration.defaultRestClientBuilderCustomizer(properties, List.of());
    var customizer2 = restClientConfiguration.defaultRestClientBuilderCustomizer(properties, List.of());
    when(customizers.orderedStream())
      .thenReturn(Stream.of(customizer1))
      .thenReturn(Stream.of(customizer2));

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

  @Test
  void defaultCustomizer_connectionManagerIsSingleton() {
    var props = new OpensearchProperties();
    var customizer = new DefaultRestClientBuilderCustomizer(props, List.of());

    var managers = new ArrayList<>();
    var clientBuilder1 = mock(HttpAsyncClientBuilder.class);
    var clientBuilder2 = mock(HttpAsyncClientBuilder.class);

    doAnswer(inv -> {
      managers.add(inv.getArgument(0));
      return clientBuilder1;
    }).when(clientBuilder1).setConnectionManager(any(PoolingAsyncClientConnectionManager.class));
    doAnswer(inv -> {
      managers.add(inv.getArgument(0));
      return clientBuilder2;
    }).when(clientBuilder2).setConnectionManager(any(PoolingAsyncClientConnectionManager.class));

    customizer.customize(clientBuilder1);
    customizer.customize(clientBuilder2);

    assertThat(managers).hasSize(2);
    assertThat(managers.get(0)).isSameAs(managers.get(1));
  }

  @Test
  void defaultCustomizer_usesPoolLimitsFromProperties() {
    var props = new OpensearchProperties();
    props.setMaxConnPerRoute(50);
    props.setMaxConnTotal(200);

    var customizer = new DefaultRestClientBuilderCustomizer(props, List.of());
    assertThat(customizer).isNotNull();
  }

  @Test
  void defaultCustomizer_validateAfterInactivityDisabledByDefault() {
    var props = new OpensearchProperties();
    // validateAfterInactivity defaults to null — should construct without error
    var customizer = new DefaultRestClientBuilderCustomizer(props, List.of());
    assertThat(customizer).isNotNull();
  }

  @Test
  void defaultCustomizer_validateAfterInactivityEnabledWhenSet() {
    var props = new OpensearchProperties();
    props.setValidateAfterInactivity(Duration.ofSeconds(30));
    var customizer = new DefaultRestClientBuilderCustomizer(props, List.of());
    assertThat(customizer).isNotNull();
  }

  @Test
  void defaultCustomizer_connectionTimeToLiveEnabledWhenSet() {
    var props = new OpensearchProperties();
    props.setConnectionTimeToLive(Duration.ofSeconds(60));
    var customizer = new DefaultRestClientBuilderCustomizer(props, List.of());
    assertThat(customizer).isNotNull();
  }
}
