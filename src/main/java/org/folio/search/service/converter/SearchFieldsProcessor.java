package org.folio.search.service.converter;

import static java.util.Collections.emptyMap;
import static org.folio.search.utils.LogUtils.collectionToLogMsg;
import static org.folio.search.utils.SearchConverterUtils.getNewAsMap;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.folio.search.model.converter.ConversionContext;
import org.folio.search.model.metadata.SearchFieldDescriptor;
import org.folio.search.service.consortium.FeatureConfigServiceDecorator;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.utils.JsonConverter;
import org.folio.search.utils.SearchUtils;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class SearchFieldsProcessor {

  private final JsonConverter jsonConverter;
  private final FeatureConfigServiceDecorator featureConfigService;
  private final Map<String, FieldProcessor<?, ?>> fieldProcessors;

  private final Map<String, AtomicLong> processorTimingNs = new ConcurrentHashMap<>();
  private final AtomicLong deserializeNs = new AtomicLong();
  private final AtomicLong profilingCount = new AtomicLong();

  /**
   * Provides search fields as {@link Map} for given resource in the {@link ConversionContext} object.
   *
   * @param ctx resource conversion context as {@link ConversionContext} object
   * @return map with retrieved search fields
   */
  public Map<String, Object> getSearchFields(ConversionContext ctx) {
    log.debug("getSearchFields:: by [resourceEvent: {}, languages: {}]",
      ctx.getResourceEvent(), collectionToLogMsg(ctx.getLanguages()));

    var resourceDescription = ctx.getResourceDescription();
    var searchFields = resourceDescription.getSearchFields();
    if (MapUtils.isEmpty(searchFields)) {
      return emptyMap();
    }
    var data = getNewAsMap(ctx.getResourceEvent());
    var resourceClass = resourceDescription.getEventBodyJavaClass();
    long ds0 = System.nanoTime();
    var resourceObject = resourceClass != null ? jsonConverter.convert(data, resourceClass) : data;
    deserializeNs.addAndGet(System.nanoTime() - ds0);

    var resultMap = new LinkedHashMap<String, Object>();
    searchFields.forEach((name, fieldDescriptor) -> {
      var resource = fieldDescriptor.isRawProcessing() ? data : resourceObject;
      if (isSearchProcessorEnabled(fieldDescriptor)) {
        long t0 = System.nanoTime();
        resultMap.putAll(getSearchFieldValue(resource, ctx.getLanguages(), name, fieldDescriptor));
        long elapsed = System.nanoTime() - t0;
        processorTimingNs.computeIfAbsent(fieldDescriptor.getProcessor(), k -> new AtomicLong()).addAndGet(elapsed);
      }
    });

    long count = profilingCount.incrementAndGet();
    if (count % 5000 == 0) {
      logProcessorProfile(count);
    }
    return resultMap;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> getSearchFieldValue(
    Object resource, List<String> languages, String name, SearchFieldDescriptor descriptor) {

    var fieldProcessor = (FieldProcessor<Object, ?>) fieldProcessors.get(descriptor.getProcessor());
    try {
      var value = fieldProcessor.getFieldValue(resource);
      if (ObjectUtils.isNotEmpty(value)) {
        return SearchUtils.getPlainFieldValue(descriptor, name, value, languages);
      }
    } catch (Exception e) {
      log.warn("Failed to retrieve field value", e);
    }

    return emptyMap();
  }

  public void logProfilingSummary() {
    long count = profilingCount.get();
    if (count > 0) {
      logProcessorProfile(count);
    }
    resetProfilingCounters();
  }

  private void logProcessorProfile(long count) {
    var sb = new StringBuilder();
    sb.append("getSearchFields:: profile [records: ").append(count);
    sb.append(", deserialize: ").append(deserializeNs.get() / 1_000_000).append("ms");
    processorTimingNs.entrySet().stream()
      .sorted(Comparator.<Map.Entry<String, AtomicLong>, Long>comparing(e -> e.getValue().get()).reversed())
      .forEach(e -> {
        String shortName = e.getKey();
        int dot = shortName.lastIndexOf('.');
        if (dot >= 0) {
          shortName = shortName.substring(dot + 1);
        }
        sb.append(", ").append(shortName).append(": ").append(e.getValue().get() / 1_000_000).append("ms");
      });
    sb.append("]");
    log.info(sb.toString());
  }

  private void resetProfilingCounters() {
    processorTimingNs.clear();
    deserializeNs.set(0);
    profilingCount.set(0);
  }

  private boolean isSearchProcessorEnabled(SearchFieldDescriptor desc) {
    var dependsOnFeature = desc.getDependsOnFeature();
    return dependsOnFeature == null || featureConfigService.isEnabled(dependsOnFeature);
  }
}
