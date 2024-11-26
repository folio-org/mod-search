package org.folio.search.service.setter;

import static org.folio.search.utils.CollectionUtils.noneMatch;
import static org.folio.search.utils.SearchUtils.MULTILANG_SOURCE_SUBFIELD;
import static org.folio.search.utils.SearchUtils.updateMultilangPlainFieldKey;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Predicate;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.model.service.MultilangValue;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractAllValuesProcessor implements FieldProcessor<Map<String, Object>, MultilangValue> {

  protected final Set<String> excludedFieldEndings = Set.of("Id", "Ids");
  protected SearchFieldProvider searchFieldProvider;

  /**
   * Uses to inject {@link SearchFieldProvider} bean by dependency injection framework.
   *
   * @param localSearchFieldProvider {@link SearchFieldProvider} bean.
   */
  @Autowired
  public void setSearchFieldProvider(SearchFieldProvider localSearchFieldProvider) {
    this.searchFieldProvider = localSearchFieldProvider;
  }

  protected MultilangValue getAllFieldValues(Object eventBody, String initialPath, Predicate<String> keyFilter) {
    var multilangValue = MultilangValue.empty();
    if (ObjectUtils.isEmpty(eventBody)) {
      return multilangValue;
    }

    collectFieldValuesFromEventBody(initialPath, multilangValue, eventBody, keyFilter);
    return multilangValue;
  }

  protected boolean isIncludedField(String fieldName) {
    return noneMatch(excludedFieldEndings, fieldName::endsWith);
  }

  private void collectFieldValuesFromEventBody(String path, MultilangValue context,
                                               Map<String, Object> fields, Predicate<String> keyFilter) {
    if (MapUtils.isEmpty(fields)) {
      return;
    }

    var sourceLanguageValue = fields.get(MULTILANG_SOURCE_SUBFIELD);
    if (sourceLanguageValue != null) {
      return;
    }

    for (Entry<String, Object> entry : fields.entrySet()) {
      String key = entry.getKey();
      if (isIncludedField(key) && keyFilter.test(key)) {
        var newKey = updateMultilangPlainFieldKey(key);
        var newPath = path != null ? path + "." + newKey : newKey;
        collectFieldValuesFromEventBody(newPath, context, entry.getValue(), keyFilter);
      }
    }
  }

  private void collectFieldValuesFromEventBody(String path, MultilangValue ctx,
                                               Collection<?> collection, Predicate<String> keyFilter) {
    if (CollectionUtils.isNotEmpty(collection)) {
      collection.forEach(value -> collectFieldValuesFromEventBody(path, ctx, value, keyFilter));
    }
  }

  @SuppressWarnings("unchecked")
  private void collectFieldValuesFromEventBody(String path, MultilangValue ctx, Object v, Predicate<String> filter) {
    if (v instanceof String) {
      ctx.addValue(StringUtils.strip((String) v),
        searchFieldProvider.isFullTextField(ResourceType.INSTANCE, path));
    }

    if (v instanceof Collection<?>) {
      collectFieldValuesFromEventBody(path, ctx, (Collection<?>) v, filter);
    }

    if (v instanceof Map<?, ?>) {
      collectFieldValuesFromEventBody(path, ctx, (Map<String, Object>) v, filter);
    }
  }
}
