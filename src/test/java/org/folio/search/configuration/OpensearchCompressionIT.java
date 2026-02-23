package org.folio.search.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.opensearch.client.RequestOptions.DEFAULT;
import static org.opensearch.index.query.QueryBuilders.matchAllQuery;
import static org.opensearch.search.builder.SearchSourceBuilder.searchSource;

import java.io.IOException;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.base.BaseIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opensearch.action.search.SearchRequest;
import org.springframework.test.context.TestPropertySource;

/**
 * Verifies that OpenSearch responses are correctly parsed when gzip compression is enabled.
 *
 * <p>opensearch-rest-client 3.x removed the manual {@code GzipDecompressingEntity} wrapping from
 * {@code RestClient.convertResponse()}, relying on httpclient5 5.6's automatic async gzip
 * decompression. Without httpclient5 5.6 on the classpath, compressed responses are returned as
 * raw bytes and JSON parsing fails.
 */
@IntegrationTest
@TestPropertySource(properties = "spring.opensearch.compression-enabled=true")
class OpensearchCompressionIT extends BaseIntegrationTest {

  @BeforeAll
  static void prepare() {
    enableTenant(TENANT_ID);
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @Test
  void shouldParseSearchResponseWhenCompressionEnabled() throws IOException {
    var request = new SearchRequest()
      .source(searchSource().query(matchAllQuery()).trackTotalHits(true));

    var response = elasticClient.search(request, DEFAULT);

    assertThat(response.getHits().getTotalHits()).isNotNull();
  }
}
