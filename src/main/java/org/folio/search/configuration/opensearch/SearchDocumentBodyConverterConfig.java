package org.folio.search.configuration.opensearch;

import java.util.Map;
import java.util.function.Function;
import org.folio.search.utils.JsonConverter;
import org.folio.search.utils.SmileConverter;
import org.opensearch.common.bytes.BytesReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SearchDocumentBodyConverterConfig {

  @Bean
  @ConditionalOnProperty(prefix = "folio.search-config.indexing", name = "data-format", havingValue = "json")
  public Function<Map<String, Object>, BytesReference> jsonSearchDocumentBodyConverter(JsonConverter jsonConverter) {
    return jsonConverter::toJsonBytes;
  }

  @Bean
  @ConditionalOnProperty(prefix = "folio.search-config.indexing", name = "data-format", havingValue = "smile")
  public Function<Map<String, Object>, BytesReference> smileSearchDocumentBodyConverter(SmileConverter smileConverter) {
    return smileConverter::toSmile;
  }
}
