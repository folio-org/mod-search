package org.folio.search.service.setter.item;

import static org.folio.search.utils.LogUtils.collectionToLogMsg;
import static org.opensearch.index.query.QueryBuilders.idsQuery;

import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.MapUtils;
import org.folio.search.domain.dto.ItemSearchRecord;
import org.folio.search.model.SimpleResourceRequest;
import org.folio.search.model.types.ResourceType;
import org.folio.search.repository.SearchRepository;
import org.folio.search.service.consortium.TenantProvider;
import org.folio.search.service.setter.SearchResponsePostProcessor;
import org.folio.spring.FolioExecutionContext;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public final class ItemSearchResponsePostProcessor implements SearchResponsePostProcessor<ItemSearchRecord> {

  private static final String INSTANCE_TITLE_FIELD = "plain_title";

  private final SearchRepository searchRepository;
  private final FolioExecutionContext context;
  private final TenantProvider tenantProvider;

  @Override
  public Class<ItemSearchRecord> getGeneric() {
    return ItemSearchRecord.class;
  }

  @Override
  public void process(List<ItemSearchRecord> items) {
    log.debug("process:: by [items: {}]", collectionToLogMsg(items, true));

    if (items == null || items.isEmpty()) {
      return;
    }

    var instanceIds = items.stream()
      .map(ItemSearchRecord::getInstanceId)
      .filter(Objects::nonNull)
      .distinct()
      .toList();
    if (instanceIds.isEmpty()) {
      return;
    }

    var resourceRequest = new SimpleResourceRequest(ResourceType.INSTANCE,
      tenantProvider.getTenant(context.getTenantId()));
    var searchHits = searchRepository.search(resourceRequest, buildQuery(instanceIds)).getHits().getHits();

    for (var searchHit : searchHits) {
      var instanceTitle = MapUtils.getString(searchHit.getSourceAsMap(), INSTANCE_TITLE_FIELD);
      items.stream()
        .filter(item -> searchHit.getId().equals(item.getInstanceId()))
        .forEach(item -> item.setInstanceTitle(instanceTitle));
    }
  }

  private SearchSourceBuilder buildQuery(List<String> instanceIds) {
    return new SearchSourceBuilder()
      .query(idsQuery().addIds(instanceIds.toArray(String[]::new)))
      .size(instanceIds.size())
      .fetchSource(INSTANCE_TITLE_FIELD, null)
      .trackTotalHits(true);
  }
}
