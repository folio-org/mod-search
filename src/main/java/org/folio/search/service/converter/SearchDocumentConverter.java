package org.folio.search.service.converter;

import static org.folio.search.model.types.IndexActionType.DELETE;
import static org.folio.search.model.types.IndexActionType.INDEX;
import static org.folio.search.utils.CollectionUtils.mergeSafely;
import static org.folio.search.utils.SearchConverterUtils.getMapValueByPath;
import static org.folio.search.utils.SearchConverterUtils.getNewAsMap;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import lombok.extern.log4j.Log4j2;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.model.converter.ConversionContext;
import org.folio.search.model.index.SearchDocumentBody;
import org.folio.search.model.types.IndexingDataFormat;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.consortium.LanguageConfigServiceDecorator;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.folio.search.utils.ResourceFieldMapper;
import org.folio.search.utils.SearchConverterUtils;
import org.opensearch.core.common.bytes.BytesReference;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class SearchDocumentConverter {

  private final SearchFieldsProcessor searchFieldsProcessor;
  private final LanguageConfigServiceDecorator languageConfigService;
  private final ResourceDescriptionService descriptionService;
  private final IndexingDataFormat indexingDataFormat;
  private final Function<Map<String, Object>, BytesReference> searchDocumentBodyConverter;

  // Profiling accumulators
  private long convertFieldMapNs;
  private long convertSearchFieldsNs;
  private long convertSerializeNs;
  private long convertCount;

  public SearchDocumentConverter(SearchFieldsProcessor searchFieldsProcessor,
                                 LanguageConfigServiceDecorator languageConfigService,
                                 ResourceDescriptionService descriptionService,
                                 SearchConfigurationProperties searchConfigurationProperties,
                                 Function<Map<String, Object>, BytesReference> searchDocumentBodyConverter) {
    this.searchFieldsProcessor = searchFieldsProcessor;
    this.languageConfigService = languageConfigService;
    this.descriptionService = descriptionService;
    this.indexingDataFormat = searchConfigurationProperties.getIndexing().getDataFormat();
    this.searchDocumentBodyConverter = searchDocumentBodyConverter;
  }

  public void resetProfilingCounters() {
    convertFieldMapNs = 0;
    convertSearchFieldsNs = 0;
    convertSerializeNs = 0;
    convertCount = 0;
  }

  public void logProfilingSummary() {
    if (convertCount > 0) {
      log.info("convert:: SUMMARY [records: {}, fieldMap: {}ms (avg: {}us), "
          + "searchFields: {}ms (avg: {}us), serialize: {}ms (avg: {}us)]",
        convertCount,
        convertFieldMapNs / 1_000_000, convertFieldMapNs / convertCount / 1_000,
        convertSearchFieldsNs / 1_000_000, convertSearchFieldsNs / convertCount / 1_000,
        convertSerializeNs / 1_000_000, convertSerializeNs / convertCount / 1_000);
    }
    resetProfilingCounters();
    searchFieldsProcessor.logProfilingSummary();
  }

  /**
   * Converts {@link ResourceEvent} object to the {@link SearchDocumentBody} objects.
   *
   * @param resourceEvent - resource event for conversion to Elasticsearch document
   * @return list with elasticsearch documents.
   */
  public Optional<SearchDocumentBody> convert(ResourceEvent resourceEvent) {
    log.debug("convert:: by [resourceEvent: {}]", resourceEvent);

    if (resourceEvent.getType() == ResourceEventType.DELETE) {
      log.debug("convert:: resourceEvent.Type == DELETE");
      return Optional.of(SearchDocumentBody.of(null, indexingDataFormat, resourceEvent, DELETE));
    }

    return canConvertEvent(resourceEvent)
      ? Optional.of(convert(buildConversionContext(resourceEvent)))
      : Optional.empty();
  }

  private SearchDocumentBody convert(ConversionContext context) {
    var resourceEvent = context.getResourceEvent();
    var resourceDescriptionFields = context.getResourceDescription().getFields();

    var t0 = System.nanoTime();
    var baseFields = ResourceFieldMapper.convertMapUsingResourceFields(
      getNewAsMap(resourceEvent), resourceDescriptionFields, context.getLanguages(), context.getTenantId());
    var t1 = System.nanoTime();
    var searchFields = searchFieldsProcessor.getSearchFields(context);
    var t2 = System.nanoTime();
    var resultDocument = mergeSafely(baseFields, searchFields);
    final var resultBody = searchDocumentBodyConverter.apply(resultDocument);
    var t3 = System.nanoTime();

    convertFieldMapNs += t1 - t0;
    convertSearchFieldsNs += t2 - t1;
    convertSerializeNs += t3 - t2;
    convertCount++;
    if (convertCount % 5000 == 0) {
      log.info("convert:: profile [records: {}, fieldMap: {}ms, searchFields: {}ms, serialize: {}ms]",
        convertCount,
        convertFieldMapNs / 1_000_000,
        convertSearchFieldsNs / 1_000_000,
        convertSerializeNs / 1_000_000);
    }

    return SearchDocumentBody.of(resultBody, indexingDataFormat, resourceEvent, INDEX);
  }

  private List<String> getResourceLanguages(List<String> languageSource, Map<String, Object> resourceData) {
    var supportedLanguages = languageConfigService.getAllLanguageCodes();
    return languageSource.stream()
      .map(sourcePath -> getMapValueByPath(sourcePath, resourceData))
      .flatMap(SearchConverterUtils::getStringStreamFromValue)
      .distinct()
      .filter(supportedLanguages::contains)
      .toList();
  }

  private static boolean canConvertEvent(ResourceEvent resourceEvent) {
    return resourceEvent.getNew() instanceof Map;
  }

  private ConversionContext buildConversionContext(ResourceEvent event) {
    var resourceDescription = descriptionService.get(ResourceType.byName(event.getResourceName()));
    var resourceData = getNewAsMap(event);
    var resourceLanguages = getResourceLanguages(resourceDescription.getLanguageSourcePaths(), resourceData);
    return ConversionContext.of(event, resourceDescription, resourceLanguages, event.getTenant());
  }

}
