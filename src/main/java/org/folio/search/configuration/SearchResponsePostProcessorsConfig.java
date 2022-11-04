package org.folio.search.configuration;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.folio.search.service.setter.SearchResponsePostProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SearchResponsePostProcessorsConfig {
  @Autowired
  List<SearchResponsePostProcessor<?>> searchResponsePostProcessorList;

  @Bean
  public Map<Class<?>, SearchResponsePostProcessor<?>> searchResponsePostProcessorMap() {
    return searchResponsePostProcessorList.stream()
      .collect(Collectors
        .toMap(SearchResponsePostProcessor::getGeneric, Function.identity()));
  }

}
