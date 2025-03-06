package org.folio.search.service.setter.callnumber;

import static org.folio.search.utils.LogUtils.collectionToLogMsg;
import static org.opensearch.index.query.QueryBuilders.idsQuery;

import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.MapUtils;
import org.folio.search.model.SimpleResourceRequest;
import org.folio.search.model.index.CallNumberResource;
import org.folio.search.model.index.InstanceSubResource;
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
public final class CallNumberSearchResponsePostProcessor implements SearchResponsePostProcessor<CallNumberResource> {

  private static final String INSTANCE_TITLE_FIELD = "plain_title";

  private final SearchRepository searchRepository;
  private final FolioExecutionContext context;
  private final TenantProvider tenantProvider;

  @Override
  public Class<CallNumberResource> getGeneric() {
    return CallNumberResource.class;
  }

  @Override
  public void process(List<CallNumberResource> res) {
    log.debug("process:: by [res: {}]", collectionToLogMsg(res, true));

    if (res == null || res.isEmpty()) {
      return;
    }
    var subResources = res.stream()
      .flatMap(resource -> resource.instances().stream())
      .toList();

    countAndSetNumberOfLinkedInstances(subResources);
  }

  private void countAndSetNumberOfLinkedInstances(List<InstanceSubResource> authorities) {
    var ids = authorities.stream()
      .map(InstanceSubResource::getInstanceId)
      .filter(instanceIds -> instanceIds.size() == 1)
      .flatMap(Collection::stream)
      .distinct()
      .toList();
    var queries = buildQuery(ids);

    var resourceRequest = SimpleResourceRequest.of(ResourceType.INSTANCE,
      tenantProvider.getTenant(context.getTenantId()));
    var searchHits = searchRepository.search(resourceRequest, queries).getHits().getHits();

    for (var searchHit : searchHits) {
      var instanceId = searchHit.getId();
      var instanceTitle = MapUtils.getString(searchHit.getSourceAsMap(), INSTANCE_TITLE_FIELD);
      for (var authority : authorities) {
        if (authority.getInstanceId().size() == 1 && authority.getInstanceId().get(0).equals(instanceId)) {
          authority.setInstanceTitle(instanceTitle);
        }
      }
    }
  }

  private SearchSourceBuilder buildQuery(List<String> instanceIds) {
    var boolQueryBuilder = idsQuery().addIds(instanceIds.toArray(String[]::new));

    return new SearchSourceBuilder()
      .query(boolQueryBuilder)
      .size(instanceIds.size())
      .fetchSource(INSTANCE_TITLE_FIELD, null)
      .trackTotalHits(true);
  }
}
