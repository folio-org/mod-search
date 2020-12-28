package org.folio.search.service.metadata;

import static java.util.Collections.unmodifiableMap;

import java.util.Map;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.folio.search.exception.ResourceDescriptionException;
import org.folio.search.model.metadata.SearchFieldType;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocalSearchFieldProvider implements SearchFieldProvider {

  private final LocalResourceProvider localResourceProvider;
  private Map<String, SearchFieldType> elasticsearchFieldTypes;

  @Override
  public SearchFieldType getSearchFieldType(String fieldType) {
    var indexFieldType = elasticsearchFieldTypes.get(fieldType);
    if (indexFieldType == null) {
      throw new ResourceDescriptionException(String.format(
        "Failed to find search field type [fieldType: %s]", fieldType));
    }
    return indexFieldType;
  }

  /**
   * Loads local defined elasticsearch field type from json.
   */
  @PostConstruct
  public void init() {
    elasticsearchFieldTypes = unmodifiableMap(localResourceProvider.getSearchFieldTypes());
  }
}
