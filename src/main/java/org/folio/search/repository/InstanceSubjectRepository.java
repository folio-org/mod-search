package org.folio.search.repository;

import static java.util.Collections.emptyList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.collections4.MapUtils.getString;
import static org.apache.commons.lang3.StringUtils.toRootLowerCase;
import static org.elasticsearch.client.RequestOptions.DEFAULT;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;
import static org.elasticsearch.search.builder.SearchSourceBuilder.searchSource;
import static org.folio.search.model.types.IndexActionType.DELETE;
import static org.folio.search.model.types.IndexActionType.INDEX;
import static org.folio.search.utils.BrowseUtils.SUBJECT_BROWSING_FIELD;
import static org.folio.search.utils.BrowseUtils.getSubjectCounts;
import static org.folio.search.utils.BrowseUtils.getSubjectCountsQuery;
import static org.folio.search.utils.CollectionUtils.toLinkedHashMap;
import static org.folio.search.utils.SearchConverterUtils.getOldAsMap;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.SearchUtils.INSTANCE_SUBJECT_RESOURCE;
import static org.folio.search.utils.SearchUtils.getElasticsearchIndexName;
import static org.folio.search.utils.SearchUtils.performExceptionalOperation;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkItemResponse.Failure;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.model.Pair;
import org.folio.search.model.SimpleResourceRequest;
import org.folio.search.model.index.SearchDocumentBody;
import org.springframework.stereotype.Repository;

@Log4j2
@Repository
@RequiredArgsConstructor
public class InstanceSubjectRepository extends AbstractResourceRepository {

  static final RequestOptions OPTIMISTIC_LOCKING_REQUEST_OPTIONS = getOptimisticLockingOptions();

  private final IndexRepository indexRepository;
  private final SearchRepository searchRepository;
  private final SearchConfigurationProperties searchConfigurationProperties;

  @Override
  public FolioIndexOperationResponse indexResources(List<SearchDocumentBody> documents) {
    var documentByAction = documents.stream().collect(groupingBy(SearchDocumentBody::getAction));
    var bulkIndexResponse = super.indexResources(documentByAction.get(INDEX));
    var documentToDelete = documentByAction.get(DELETE);
    return CollectionUtils.isEmpty(documentToDelete) ? bulkIndexResponse : deleteUnusedSubjects(documentToDelete);
  }

  private FolioIndexOperationResponse deleteUnusedSubjects(List<SearchDocumentBody> documentToDelete) {
    var subjectByTenant = documentToDelete.stream().collect(groupingBy(SearchDocumentBody::getRouting));
    subjectByTenant.forEach(this::deleteUnusedSubjectsPerTenant);
    return getSuccessIndexOperationResponse();
  }

  private void deleteUnusedSubjectsPerTenant(String tenant, List<SearchDocumentBody> documents) {
    var retryAttempts = searchConfigurationProperties.getIndexing().getInstanceSubjects().getRetryAttempts();
    var retryCounter = new AtomicInteger(0);
    List<String> remainingIds;
    var remainingDocuments = documents;
    var documentsById = remainingDocuments.stream().collect(toLinkedHashMap(SearchDocumentBody::getId, identity()));

    do {
      remainingIds = deleteSubjects(tenant, remainingDocuments);
      remainingDocuments = getRemainingDocuments(remainingIds, documentsById);
    } while (isNotEmpty(remainingIds) && retryCounter.getAndIncrement() < retryAttempts);

    if (isNotEmpty(remainingIds)) {
      log.warn("Failed to delete subject by ids [subjectIds: {}]", remainingIds);
    }
  }

  private List<String> deleteSubjects(String tenant, List<SearchDocumentBody> docs) {
    var documentsBySubjects = docs.stream().collect(toLinkedHashMap(InstanceSubjectRepository::getSubject, identity()));
    var subjects = documentsBySubjects.keySet();

    log.debug("Removing subjects from instance_subject index [tenantId: {}, subjects: {}]", tenant, subjects);
    indexRepository.refreshIndex(getElasticsearchIndexName(INSTANCE_RESOURCE, tenant));
    var seqNumbersById = getSearchHitsBySubjectIds(tenant, docs);

    var resourceRequest = SimpleResourceRequest.of(INSTANCE_RESOURCE, tenant);
    var subjectCountResponse = searchRepository.search(resourceRequest, getSubjectCountsQuery(subjects));
    var subjectCounts = getSubjectCounts(subjectCountResponse);

    return deleteSubjects(subjectCounts, documentsBySubjects, seqNumbersById);
  }

  private List<String> deleteSubjects(Map<String, Long> subjectCounts,
    Map<String, SearchDocumentBody> docsBySubject, Map<String, SearchHit> searchHitsById) {
    Map<String, DocWriteRequest<?>> deleteRequestsBySubject = docsBySubject.keySet().stream()
      .filter(subject -> subjectCounts.getOrDefault(subject, 0L) == 0L)
      .map(subject -> Pair.of(subject, prepareDeleteRequest(subject, docsBySubject, searchHitsById)))
      .filter(pair -> pair.getSecond() != null)
      .collect(toLinkedHashMap(Pair::getFirst, Pair::getSecond));

    if (deleteRequestsBySubject.isEmpty()) {
      return emptyList();
    }

    log.debug("Deleting subjects [subjects: {}]", deleteRequestsBySubject.keySet());
    var bulkRequest = new BulkRequest().add(deleteRequestsBySubject.values());
    return getFailedDocumentIds(executeBulkRequest(bulkRequest));
  }

  private Map<String, SearchHit> getSearchHitsBySubjectIds(String tenant, List<SearchDocumentBody> documents) {
    var indexName = getElasticsearchIndexName(INSTANCE_SUBJECT_RESOURCE, tenant);
    var request = new SearchRequest(indexName).source(getSubjectIdsQuery(documents));

    var searchResponse = performExceptionalOperation(
      () -> elasticsearchClient.search(request, OPTIMISTIC_LOCKING_REQUEST_OPTIONS),
      indexName, "searchApi");

    return Optional.ofNullable(searchResponse)
      .map(SearchResponse::getHits)
      .map(SearchHits::getHits)
      .stream()
      .flatMap(Arrays::stream)
      .collect(toLinkedHashMap(SearchHit::getId, identity()));
  }

  private static SearchSourceBuilder getSubjectIdsQuery(List<SearchDocumentBody> documents) {
    var documentIds = documents.stream().map(SearchDocumentBody::getId).distinct().toArray(String[]::new);
    return searchSource().query(idsQuery().addIds(documentIds)).from(0).size(documentIds.length).fetchSource(false);
  }

  private static List<SearchDocumentBody> getRemainingDocuments(
    List<String> ids, Map<String, SearchDocumentBody> documentsById) {
    return ids.stream().map(documentsById::get).filter(Objects::nonNull).collect(toList());
  }

  private static String getSubject(SearchDocumentBody document) {
    var resourcePayload = getOldAsMap(document.getResourceEvent());
    return toRootLowerCase(getString(resourcePayload, SUBJECT_BROWSING_FIELD));
  }

  private static DocWriteRequest<?> prepareDeleteRequest(String subject,
    Map<String, SearchDocumentBody> searchDocumentBySubject, Map<String, SearchHit> searchHitsById) {
    var doc = searchDocumentBySubject.get(subject);
    var hit = searchHitsById.get(doc.getId());
    return hit != null ? prepareDeleteRequest(doc, hit.getSeqNo(), hit.getPrimaryTerm()) : null;
  }

  private static DeleteRequest prepareDeleteRequest(SearchDocumentBody doc, Long seqNo, Long primaryTerm) {
    return new DeleteRequest(doc.getIndex())
      .id(doc.getId())
      .routing(doc.getRouting())
      .setIfSeqNo(seqNo)
      .setIfPrimaryTerm(primaryTerm);
  }

  private static List<String> getFailedDocumentIds(BulkResponse bulkResponse) {
    return Arrays.stream(bulkResponse.getItems())
      .filter(BulkItemResponse::isFailed)
      .map(BulkItemResponse::getFailure)
      .map(Failure::getId)
      .collect(toList());
  }

  private static RequestOptions getOptimisticLockingOptions() {
    return DEFAULT.toBuilder().addParameter("seq_no_primary_term", "true").build();
  }
}
