package org.folio.search.cql;

import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static org.folio.search.model.types.SearchType.SORT;
import static org.opensearch.search.sort.SortBuilders.fieldSort;
import static org.opensearch.search.sort.SortMode.MAX;
import static org.opensearch.search.sort.SortMode.MIN;
import static org.opensearch.search.sort.SortOrder.ASC;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.cql2pgjson.exception.CQLFeatureUnsupportedException;
import org.folio.cql2pgjson.model.CqlModifiers;
import org.folio.cql2pgjson.model.CqlSort;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.metadata.PlainFieldDescription;
import org.folio.search.model.types.ResourceType;
import org.folio.search.model.types.SortFieldType;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.opensearch.search.sort.FieldSortBuilder;
import org.opensearch.search.sort.SortBuilder;
import org.opensearch.search.sort.SortOrder;
import org.springframework.stereotype.Component;
import org.z3950.zing.cql.CQLSortNode;
import org.z3950.zing.cql.ModifierSet;

@Component
@RequiredArgsConstructor
public class CqlSortProvider {

  private static final String DEFAULT_SORT_FIELD_PREFIX = "sort_";
  private final SearchFieldProvider searchFieldProvider;

  /**
   * Provides {@link List} of {@link SortBuilder} objects for passed {@link CQLSortNode} object and resource name.
   *
   * @param sortNode sort node as {@link CQLSortNode} object
   * @param resource resource name as {@link String} object
   * @return {@link List} of {@link SortBuilder} objects
   */
  public List<SortBuilder<FieldSortBuilder>> getSort(CQLSortNode sortNode, ResourceType resource) {
    return sortNode.getSortIndexes().stream()
      .map(sortIndex -> buildSortForField(sortIndex, resource))
      .flatMap(Collection::stream)
      .toList();
  }

  private List<SortBuilder<FieldSortBuilder>> buildSortForField(ModifierSet sortIndex, ResourceType resource) {
    var sortField = searchFieldProvider.getModifiedField(sortIndex.getBase(), resource);
    var sortFieldDesc = getValidSortField(resource, sortField);
    var esSortOrder = getSortOrder(getCqlModifiers(sortIndex).getCqlSort());

    var sortDescription = sortFieldDesc.getSortDescription();
    if (sortDescription == null) {
      return singletonList(fieldSort(DEFAULT_SORT_FIELD_PREFIX + sortField).order(esSortOrder));
    }

    var sortType = sortDescription.getSortType();
    var fieldName = Objects.toString(sortDescription.getFieldName(), DEFAULT_SORT_FIELD_PREFIX + sortField);
    return sortType == SortFieldType.COLLECTION
           ? buildSortForCollection(esSortOrder, fieldName, sortDescription.getSecondarySort())
           : singletonList(fieldSort(fieldName).order(esSortOrder));
  }

  private static List<SortBuilder<FieldSortBuilder>> buildSortForCollection(
    SortOrder order, String field, List<String> secondarySortFields) {
    var sort = new ArrayList<SortBuilder<FieldSortBuilder>>();
    sort.add(buildSortForCollectionField(field, order));
    if (CollectionUtils.isNotEmpty(secondarySortFields)) {
      secondarySortFields.forEach(secondaryField -> sort.add(buildSortForCollectionField(secondaryField, order)));
    }
    return sort;
  }

  private static FieldSortBuilder buildSortForCollectionField(String field, SortOrder sortOrder) {
    return fieldSort(field).order(sortOrder).sortMode(sortOrder == ASC ? MIN : MAX);
  }

  private PlainFieldDescription getValidSortField(ResourceType resource, String field) {
    return getSortFieldDescription(resource, field)
      .or(() -> getSortFieldDescription(resource, DEFAULT_SORT_FIELD_PREFIX + field))
      .orElseThrow(() -> new RequestValidationException("Sort field not found or cannot be used.", "sortField", field));
  }

  private CqlModifiers getCqlModifiers(ModifierSet modifierSet) {
    try {
      return new CqlModifiers(modifierSet);
    } catch (CQLFeatureUnsupportedException e) {
      throw new UnsupportedOperationException(format(
        "Failed to parse CQL query. [error: '%s']", e.getMessage()), e);
    }
  }

  private Optional<PlainFieldDescription> getSortFieldDescription(ResourceType resource, String field) {
    return searchFieldProvider.getPlainFieldByPath(resource, field)
      .filter(fieldDescription -> fieldDescription.hasType(SORT));
  }

  private static SortOrder getSortOrder(CqlSort cqlSort) {
    return cqlSort == CqlSort.DESCENDING ? SortOrder.DESC : ASC;
  }
}
