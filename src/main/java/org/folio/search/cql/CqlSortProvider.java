package org.folio.search.cql;

import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.elasticsearch.search.sort.SortBuilders.fieldSort;
import static org.elasticsearch.search.sort.SortMode.MAX;
import static org.elasticsearch.search.sort.SortMode.MIN;
import static org.elasticsearch.search.sort.SortOrder.ASC;
import static org.folio.search.model.types.SearchType.SORT;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.folio.cql2pgjson.exception.CQLFeatureUnsupportedException;
import org.folio.cql2pgjson.model.CqlModifiers;
import org.folio.cql2pgjson.model.CqlSort;
import org.folio.search.exception.ValidationException;
import org.folio.search.model.metadata.PlainFieldDescription;
import org.folio.search.model.types.SortFieldType;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.springframework.stereotype.Component;
import org.z3950.zing.cql.CQLSortNode;
import org.z3950.zing.cql.ModifierSet;

@Component
@RequiredArgsConstructor
public class CqlSortProvider {

  private final SearchFieldProvider searchFieldProvider;

  /**
   * Provides {@link List} of {@link SortBuilder} objects for passed {@link CQLSortNode} object and resource name.
   *
   * @param sortNode sort node as {@link CQLSortNode} object
   * @param resource resource name as {@link String} object
   * @return {@link List} of {@link SortBuilder} objects
   */
  public List<SortBuilder<?>> getSort(CQLSortNode sortNode, String resource) {
    return sortNode.getSortIndexes().stream()
      .map(sortIndex -> buildSortForField(sortIndex, resource))
      .flatMap(Collection::stream)
      .collect(toList());
  }

  private List<SortBuilder<?>> buildSortForField(ModifierSet sortIndex, String resource) {
    var sortField = sortIndex.getBase();
    var sortFieldDesc = getValidSortField(resource, sortField);
    var esSortOrder = getSortOrder(getCqlModifiers(sortIndex).getCqlSort());

    var sortDescription = sortFieldDesc.getSortDescription();
    if (sortDescription == null) {
      return singletonList(fieldSort("sort_" + sortField).order(esSortOrder));
    }

    var sortType = sortDescription.getSortType();
    var fieldName = StringUtils.defaultString(sortDescription.getFieldName(), "sort_" + sortField);
    return sortType == SortFieldType.COLLECTION
      ? buildSortForCollection(esSortOrder, fieldName, sortDescription.getSecondarySort())
      : singletonList(fieldSort(fieldName).order(esSortOrder));
  }

  private static List<SortBuilder<?>> buildSortForCollection(
    SortOrder order, String field, List<String> secondarySortFields) {
    var sort = new ArrayList<SortBuilder<?>>();
    sort.add(buildSortForCollectionField(field, order));
    if (CollectionUtils.isNotEmpty(secondarySortFields)) {
      secondarySortFields.forEach(secondaryField -> sort.add(buildSortForCollectionField(secondaryField, order)));
    }
    return sort;
  }

  private static FieldSortBuilder buildSortForCollectionField(String field, SortOrder sortOrder) {
    return fieldSort(field).order(sortOrder).sortMode(sortOrder == ASC ? MIN : MAX);
  }

  private PlainFieldDescription getValidSortField(String resource, String field) {
    return getSortFieldDescription(resource, field)
      .or(() -> getSortFieldDescription(resource, "sort_" + field))
      .orElseThrow(() -> new ValidationException("Sort field not found or cannot be used.", "sortField", field));
  }

  private CqlModifiers getCqlModifiers(ModifierSet modifierSet) {
    try {
      return new CqlModifiers(modifierSet);
    } catch (CQLFeatureUnsupportedException e) {
      throw new UnsupportedOperationException(format(
        "Failed to parse CQL query. [error: '%s']", e.getMessage()), e);
    }
  }

  private Optional<PlainFieldDescription> getSortFieldDescription(String resource, String field) {
    return searchFieldProvider.getPlainFieldByPath(resource, field)
      .filter(fieldDescription -> fieldDescription.hasType(SORT));
  }

  private static SortOrder getSortOrder(CqlSort cqlSort) {
    return cqlSort == CqlSort.DESCENDING ? SortOrder.DESC : ASC;
  }
}
