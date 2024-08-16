package org.folio.search.service.setter.authority;

import static org.folio.search.model.types.ResourceType.AUTHORITY;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ObjectUtils;
import org.folio.search.model.metadata.AuthorityFieldDescription;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.beans.factory.annotation.Autowired;

@Log4j2
public abstract class AbstractAuthorityProcessor implements FieldProcessor<Map<String, Object>, String> {

  private SearchFieldProvider searchFieldProvider;

  @Autowired
  public void setSearchFieldProvider(SearchFieldProvider localSearchFieldProvider) {
    this.searchFieldProvider = localSearchFieldProvider;
  }

  protected String getAuthorityType(Map<String, Object> eventBody,
                                    Function<AuthorityFieldDescription, String> valueExtractor, String defaultValue) {
    log.debug("getAuthorityType:: by [eventBody: {}, defaultValue: {}]", eventBody, defaultValue);

    return eventBody.entrySet().stream()
      .map(entry -> getAuthorityFieldForEntry(entry).map(valueExtractor))
      .flatMap(Optional::stream)
      .findFirst()
      .orElse(defaultValue);
  }

  protected Optional<AuthorityFieldDescription> getAuthorityFieldForEntry(Entry<String, Object> entry) {
    log.debug("getAuthorityFieldForEntry:: by [value: {}]", entry.getValue());

    if (ObjectUtils.isEmpty(entry.getValue())) {
      return Optional.empty();
    }

    return searchFieldProvider.getPlainFieldByPath(AUTHORITY, entry.getKey())
      .filter(AuthorityFieldDescription.class::isInstance)
      .map(AuthorityFieldDescription.class::cast);
  }
}
