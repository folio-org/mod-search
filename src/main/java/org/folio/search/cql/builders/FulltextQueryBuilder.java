package org.folio.search.cql.builders;

import static org.folio.search.utils.SearchUtils.getPathToFulltextPlainValue;
import static org.folio.search.utils.SearchUtils.isMultilangFieldPath;
import static org.folio.search.utils.SearchUtils.updatePathForFulltextField;

import lombok.Setter;
import org.folio.search.model.metadata.PlainFieldDescription;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class FulltextQueryBuilder implements TermQueryBuilder {

  @Setter(onMethod = @__(@Autowired))
  protected SearchFieldProvider searchFieldProvider;

  /**
   * Updates path for fulltext queries field using given resource name and field path.
   *
   * <p>
   * This method is required to support fulltext fields without multi-language data structure, for example {@code
   * contributors.name}.
   * </p>
   *
   * @param resource - resource name as {@link String} object
   * @param fieldPath - path to field as {@link String} object
   * @return updated path for full-text querying
   */
  protected String updatePathForFulltextQuery(String resource, String fieldPath) {
    return searchFieldProvider.getPlainFieldByPath(resource, fieldPath)
      .map(fieldDescription -> updatePathForFulltextField(fieldDescription, fieldPath))
      .orElse(fieldPath);
  }

  /**
   * Updates path for term-level queries field using given resource name and field path.
   *
   * <p>
   * This method is required to support fulltext fields without multi-language data structure, for example {@code
   * contributors.name}.
   * </p>
   *
   * @param resource - resource name as {@link String} object
   * @param fieldPath - path to field as {@link String} object
   * @return updated path for term-level querying
   */
  protected String updatePathForTermQueries(String resource, String fieldPath) {
    if (isMultilangFieldPath(fieldPath)) {
      return getPathToFulltextPlainValue(fieldPath.substring(0, fieldPath.length() - 2));
    }

    return searchFieldProvider.getPlainFieldByPath(resource, fieldPath)
      .filter(PlainFieldDescription::hasFulltextIndex)
      .map(fieldDescription -> getPathToFulltextPlainValue(fieldPath))
      .orElse(fieldPath);
  }
}
