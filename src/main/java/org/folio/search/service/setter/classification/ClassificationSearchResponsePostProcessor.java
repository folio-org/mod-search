package org.folio.search.service.setter.classification;

import static org.folio.search.utils.LogUtils.collectionToLogMsg;
import static org.opensearch.index.query.QueryBuilders.termsQuery;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.MapUtils;
import org.folio.search.model.SimpleResourceRequest;
import org.folio.search.model.index.ClassificationResource;
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
public final class ClassificationSearchResponsePostProcessor
  implements SearchResponsePostProcessor<ClassificationResource> {

  private static final String INSTANCE_TENANT_FIELD = "tenantId";
  private static final String INSTANCE_CLASSIFICATION_IDS_FIELD = "classificationIds";
  private static final String INSTANCE_TITLE_FIELD = "plain_title";
  private static final String INSTANCE_CONTRIBUTORS_FIELD = "contributors";

  private final SearchRepository searchRepository;
  private final FolioExecutionContext context;
  private final TenantProvider tenantProvider;

  @Override
  public Class<ClassificationResource> getGeneric() {
    return ClassificationResource.class;
  }

  @Override
  public void process(List<ClassificationResource> res) {
    log.debug("process:: by [res: {}]", collectionToLogMsg(res, true));

    if (res == null || res.isEmpty()) {
      return;
    }
    var subResources = res.stream()
      .flatMap(resource -> resource.instances().stream().map(subResource -> {
        subResource.setResourceId(resource.id());
        return subResource;
      }))
      .toList();

    countAndSetInstanceProperties(subResources);
  }

  private void countAndSetInstanceProperties(List<InstanceSubResource> subResources) {
    var classificationIds = subResources.stream()
      .filter(subResource -> subResource.getCount() == 1)
      .map(InstanceSubResource::getResourceId)
      .distinct()
      .toList();
    var queries = buildQuery(classificationIds);

    var resourceRequest = SimpleResourceRequest.of(ResourceType.INSTANCE,
      tenantProvider.getTenant(context.getTenantId()));
    var searchHits = searchRepository.search(resourceRequest, queries).getHits().getHits();

    for (var searchHit : searchHits) {
      var source = searchHit.getSourceAsMap();
      var tenantId = MapUtils.getString(source, INSTANCE_TENANT_FIELD);
      var classificationIdsFromSource = (List<String>) MapUtils.getObject(source, INSTANCE_CLASSIFICATION_IDS_FIELD);
      var instanceTitle = MapUtils.getString(source, INSTANCE_TITLE_FIELD);
      var instanceContributors = ((List<Map<String, String>>)  MapUtils.getObject(source, INSTANCE_CONTRIBUTORS_FIELD))
        .stream()
        .map(contributor -> contributor.get("name"))
        .toList();
      for (var subResource : subResources) {
        if (subResource.getCount() == 1
          && classificationIdsFromSource.contains(subResource.getResourceId())
          && subResource.getTenantId().equals(tenantId)) {
          subResource.setInstanceTitle(instanceTitle);
          subResource.setInstanceContributors(instanceContributors);
        }
      }
    }
  }

  private SearchSourceBuilder buildQuery(List<String> classificationIds) {
    //todo: bool query with shoulds, each should is bool with must on id, tenant
    var queryBuilder = termsQuery(INSTANCE_CLASSIFICATION_IDS_FIELD, classificationIds.toArray(String[]::new));

    return new SearchSourceBuilder()
      .query(queryBuilder)
//      .size(classificationIds.size())
      .fetchSource(new String[]{INSTANCE_TENANT_FIELD, INSTANCE_CLASSIFICATION_IDS_FIELD, INSTANCE_TITLE_FIELD,
        INSTANCE_CONTRIBUTORS_FIELD}, null)
      .trackTotalHits(true);
  }
}
