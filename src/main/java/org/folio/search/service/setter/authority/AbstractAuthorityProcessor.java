package org.folio.search.service.setter.authority;

import static org.folio.search.utils.SearchUtils.AUTHORITY_RESOURCE;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;
import org.apache.commons.lang3.ObjectUtils;
import org.folio.search.model.metadata.AuthorityFieldDescription;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractAuthorityProcessor implements FieldProcessor<Map<String, Object>, String> {

  private SearchFieldProvider searchFieldProvider;

  @Autowired
  public void setSearchFieldProvider(SearchFieldProvider localSearchFieldProvider) {
    this.searchFieldProvider = localSearchFieldProvider;
  }

  protected String getAuthorityType(Map<String, Object> eventBody,
    Function<AuthorityFieldDescription, String> valueExtractor, String defaultValue) {
    return eventBody.entrySet().stream()
      .map(entry -> getTypeForField(entry, valueExtractor))
      .flatMap(Optional::stream)
      .findFirst()
      .orElse(defaultValue);
  }

  private Optional<String> getTypeForField(Entry<String, Object> entry,
    Function<AuthorityFieldDescription, String> valueExtractor) {
    if (ObjectUtils.isEmpty(entry.getValue())) {
      return Optional.empty();
    }

    return searchFieldProvider.getPlainFieldByPath(AUTHORITY_RESOURCE, entry.getKey())
      .filter(AuthorityFieldDescription.class::isInstance)
      .map(AuthorityFieldDescription.class::cast)
      .map(valueExtractor);
  }
}
