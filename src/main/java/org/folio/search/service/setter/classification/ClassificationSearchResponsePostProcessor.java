package org.folio.search.service.setter.classification;

import static org.folio.search.utils.CollectionUtils.getValuesByPath;
import static org.folio.search.utils.LogUtils.collectionToLogMsg;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.index.query.QueryBuilders.matchQuery;

import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.MapUtils;
import org.folio.search.model.Pair;
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
  private static final String INSTANCE_CLASSIFICATION_IDS_FIELD = "classificationId";
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
    var classificationTenantPairs = subResources.stream()
      .filter(subResource -> subResource.getCount() == 1)
      .map(subResource -> Pair.of(subResource.getResourceId(), subResource.getTenantId()))
      .distinct()
      .toList();
    var queries = buildQuery(classificationTenantPairs);

    var resourceRequest = SimpleResourceRequest.of(ResourceType.INSTANCE,
      tenantProvider.getTenant(context.getTenantId()));
    var searchHits = searchRepository.search(resourceRequest, queries).getHits().getHits();

    for (var subResource : subResources) {
      if (subResource.getCount() != 1) {
        continue;
      }
      for (var searchHit : searchHits) {
        var source = searchHit.getSourceAsMap();
        var tenantId = MapUtils.getString(source, INSTANCE_TENANT_FIELD);
        var classificationIdsFromSource = getValuesByPath(source, INSTANCE_CLASSIFICATION_IDS_FIELD);
        var instanceTitle = MapUtils.getString(source, INSTANCE_TITLE_FIELD);
        var instanceContributors = getValuesByPath(source, "contributors.name");
        if (classificationIdsFromSource.contains(subResource.getResourceId())
          && Objects.equals(subResource.getTenantId(), tenantId)) {
          subResource.setInstanceTitle(instanceTitle);
          subResource.setInstanceContributors(instanceContributors);
          break;
        }
      }
    }
  }

  private SearchSourceBuilder buildQuery(List<Pair<String, String>> classificationTenantPairs) {
    var boolQueryBuilder = boolQuery();
    for (var pair : classificationTenantPairs) {
      var shouldClause = boolQuery()
        .must(matchQuery(INSTANCE_CLASSIFICATION_IDS_FIELD, pair.getFirst()));
      if (pair.getSecond() != null) {
        shouldClause.must(matchQuery(INSTANCE_TENANT_FIELD, pair.getSecond()));
      }
      boolQueryBuilder.should(shouldClause);
    }

    return new SearchSourceBuilder()
      .query(boolQueryBuilder)
      .size(10_000)
      .fetchSource(new String[]{INSTANCE_TENANT_FIELD, INSTANCE_CLASSIFICATION_IDS_FIELD, INSTANCE_TITLE_FIELD,
        INSTANCE_CONTRIBUTORS_FIELD}, null)
      .trackTotalHits(true);
  }
}
