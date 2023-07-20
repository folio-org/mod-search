package org.folio.search.service.setter.authority;

import static org.folio.search.model.index.AuthRefType.AUTHORIZED;
import static org.folio.search.utils.LogUtils.collectionToLogMsg;
import static org.folio.search.utils.SearchUtils.AUTHORITY_ID_FIELD;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.index.query.QueryBuilders.termQuery;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.Instance;
import org.folio.search.model.SimpleResourceRequest;
import org.folio.search.repository.SearchRepository;
import org.folio.search.service.consortia.TenantProvider;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.folio.search.service.setter.SearchResponsePostProcessor;
import org.folio.search.utils.SearchUtils;
import org.folio.spring.FolioExecutionContext;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public final class AuthoritySearchResponsePostProcessor implements SearchResponsePostProcessor<Authority> {

  private final SearchRepository searchRepository;
  private final SearchFieldProvider searchFieldProvider;
  private final FolioExecutionContext context;
  private final TenantProvider tenantProvider;

  @Override
  public Class<Authority> getGeneric() {
    return Authority.class;
  }

  @Override
  public void process(List<Authority> res) {
    log.debug("process:: by [res: {}]", collectionToLogMsg(res, true));

    if (res == null || res.isEmpty()) {
      return;
    }

    var authorizedAuthorities = res.stream()
      .filter(a -> AUTHORIZED.getTypeValue().equals(a.getAuthRefType()))
      .toList();

    if (!authorizedAuthorities.isEmpty()) {
      countAndSetNumberOfLinkedInstances(authorizedAuthorities);
    }
  }

  private void countAndSetNumberOfLinkedInstances(List<Authority> authorities) {
    var instanceResourceName = SearchUtils.getResourceName(Instance.class);
    var queries = buildQueries(authorities, instanceResourceName);

    var resourceRequest = SimpleResourceRequest.of(instanceResourceName,
      tenantProvider.getTenant(context.getTenantId()));
    var responses = searchRepository.msearch(resourceRequest, queries).getResponses();

    for (int i = 0; i < responses.length; i++) {
      var count = Optional.ofNullable(responses[i].getResponse())
        .map(searchResponse -> searchResponse.getHits().getTotalHits())
        .map(totalHits -> (int) totalHits.value)
        .orElse(0);
      authorities.get(i).setNumberOfTitles(count);
    }
  }

  private List<SearchSourceBuilder> buildQueries(List<Authority> authorities, String resourceName) {
    var authorityIdFields = searchFieldProvider.getFields(resourceName, AUTHORITY_ID_FIELD);
    return authorities.stream()
      .map(Authority::getId)
      .map(id -> buildQuery(authorityIdFields, id))
      .toList();
  }

  private SearchSourceBuilder buildQuery(List<String> authorityIdFields, String authorityId) {
    var boolQueryBuilder = boolQuery();

    authorityIdFields.stream()
      .map(field -> termQuery(field, authorityId))
      .forEach(boolQueryBuilder::should);

    return new SearchSourceBuilder()
      .query(boolQuery().filter(boolQueryBuilder))
      .size(0)
      .trackTotalHits(true);
  }
}
