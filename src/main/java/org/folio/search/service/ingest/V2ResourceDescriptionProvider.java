package org.folio.search.service.ingest;

import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.model.metadata.ResourceDescription;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

/**
 * Provides a filtered {@link ResourceDescription} for V2 instance enrichment.
 *
 * <p>The V2 index stores items and holdings as separate child documents, so the instance-level
 * resource description must exclude item/holdings fields and their associated search field
 * descriptors. This component deep-copies the canonical instance description at startup
 * and removes the irrelevant entries.</p>
 */
@Log4j2
@Component
@RequiredArgsConstructor
@DependsOn("resourceDescriptionService")
public class V2ResourceDescriptionProvider {

  private static final List<String> EXCLUDED_FIELD_KEYS = List.of("items", "holdings");

  private static final List<String> EXCLUDED_SEARCH_FIELD_KEYS = List.of(
    "itemPublicNotes",
    "holdingsPublicNotes",
    "itemTags",
    "holdingsTags",
    "holdingsTypeId",
    "statisticalCodes",
    "itemFullCallNumbers",
    "holdingsFullCallNumbers",
    "itemNormalizedCallNumbers",
    "holdingsNormalizedCallNumbers",
    "holdingsIdentifiers",
    "itemIdentifiers",
    "allItems",
    "allHoldings"
  );

  private final ResourceDescriptionService resourceDescriptionService;
  private ResourceDescription v2InstanceDescription;

  @PostConstruct
  void init() {
    log.info("init:: Building filtered V2 instance resource description");

    var source = resourceDescriptionService.get(ResourceType.INSTANCE);

    var filtered = new ResourceDescription();
    filtered.setName(source.getName());
    filtered.setParent(source.getParent());
    filtered.setReindexSupported(source.isReindexSupported());
    filtered.setEventBodyJavaClass(source.getEventBodyJavaClass());
    filtered.setLanguageSourcePaths(source.getLanguageSourcePaths());
    filtered.setSearchFieldModifiers(source.getSearchFieldModifiers());
    filtered.setIndexMappings(source.getIndexMappings());
    filtered.setFieldTypes(source.getFieldTypes());
    filtered.setIndexingConfiguration(source.getIndexingConfiguration());

    var fields = new LinkedHashMap<>(source.getFields());
    EXCLUDED_FIELD_KEYS.forEach(fields::remove);
    filtered.setFields(fields);

    var searchFields = new LinkedHashMap<>(source.getSearchFields());
    EXCLUDED_SEARCH_FIELD_KEYS.forEach(searchFields::remove);
    filtered.setSearchFields(searchFields);

    this.v2InstanceDescription = filtered;

    log.info("init:: V2 instance description built — fields={}, searchFields={}",
      fields.size(), searchFields.size());
  }

  /**
   * Returns the filtered V2 instance {@link ResourceDescription}.
   *
   * @return a deep-copied resource description with items/holdings fields and nested-data
   *     search field descriptors removed
   */
  public ResourceDescription getV2InstanceDescription() {
    return v2InstanceDescription;
  }
}
