package org.folio.search.repository;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.collections4.MapUtils.getString;
import static org.apache.commons.lang3.StringUtils.toRootLowerCase;
import static org.elasticsearch.client.RequestOptions.DEFAULT;
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
import static org.folio.search.utils.SearchUtils.getIndexName;
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
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetRequest;
import org.elasticsearch.action.get.MultiGetRequest.Item;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.configuration.properties.SearchConfigurationProperties.IndexingSettings;
import org.folio.search.configuration.properties.SearchConfigurationProperties.InstanceSubjectsIndexingSettings;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.model.Pair;
import org.folio.search.model.SimpleResourceRequest;
import org.folio.search.model.index.SearchDocumentBody;
import org.springframework.stereotype.Repository;

@Log4j2
@Repository
@RequiredArgsConstructor
public class InstanceSubjectRepository extends AbstractResourceRepository {

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
    var retryAttempts = getRetryAttempts();
    var retryCounter = new AtomicInteger(0);
    List<String> remainingIds;
    var remainingDocuments = documents;
    var documentsById = toLinkedHashMap(remainingDocuments, SearchDocumentBody::getId);

    do {
      remainingIds = deleteSubjects(tenant, remainingDocuments);
      remainingDocuments = getRemainingDocuments(remainingIds, documentsById);
    } while (isNotEmpty(remainingIds) && retryCounter.incrementAndGet() < retryAttempts);

    if (isNotEmpty(remainingIds)) {
      log.warn("Failed to delete subject by ids [subjectIds: {}]", remainingIds);
    }
  }

  private List<String> deleteSubjects(String tenant, List<SearchDocumentBody> docs) {
    var documentsBySubject = toLinkedHashMap(docs, InstanceSubjectRepository::getSubject);
    var subjects = documentsBySubject.keySet();

    log.debug("Removing subjects from instance_subject index [tenantId: {}, subjects: {}]", tenant, subjects);
    indexRepository.refreshIndices(getIndexName(INSTANCE_RESOURCE, tenant));

    var esDocumentsById = getSearchHitsBySubjectIds(tenant, docs);
    var resourceRequest = SimpleResourceRequest.of(INSTANCE_RESOURCE, tenant);
    var subjectCountsResponse = searchRepository.search(resourceRequest, getSubjectCountsQuery(subjects));
    return deleteSubjects(getSubjectCounts(subjectCountsResponse), documentsBySubject, esDocumentsById);
  }

  private List<String> deleteSubjects(Map<String, Long> subjectCounts,
    Map<String, SearchDocumentBody> documentsBySubject, Map<String, GetResponse> esDocumentsById) {
    Map<String, DocWriteRequest<?>> deleteRequestsBySubject = documentsBySubject.keySet().stream()
      .filter(subject -> subjectCounts.getOrDefault(subject, 0L) == 0L)
      .map(subject -> Pair.of(subject, prepareDeleteRequest(subject, documentsBySubject, esDocumentsById)))
      .filter(pair -> pair.getSecond() != null)
      .collect(toLinkedHashMap(Pair::getFirst, Pair::getSecond));

    if (deleteRequestsBySubject.isEmpty()) {
      return emptyList();
    }

    log.debug("Deleting subjects [subjects: {}]", deleteRequestsBySubject.keySet());
    var bulkRequest = new BulkRequest().add(deleteRequestsBySubject.values());
    return getFailedDocumentIds(executeBulkRequest(bulkRequest));
  }

  private Map<String, GetResponse> getSearchHitsBySubjectIds(String tenant, List<SearchDocumentBody> documents) {
    var documentByIds = performExceptionalOperation(
      () -> elasticsearchClient.mget(prepareMultiGetRequest(tenant, documents), DEFAULT),
      getIndexName(INSTANCE_SUBJECT_RESOURCE, tenant), "searchApi");

    return Optional.ofNullable(documentByIds)
      .map(MultiGetResponse::getResponses)
      .stream()
      .flatMap(Arrays::stream)
      .filter(multiGetResponse -> !multiGetResponse.isFailed())
      .map(MultiGetItemResponse::getResponse)
      .filter(GetResponse::isExists)
      .collect(toLinkedHashMap(GetResponse::getId));
  }

  private static MultiGetRequest prepareMultiGetRequest(String tenant, List<SearchDocumentBody> documents) {
    var multiGetRequest = new MultiGetRequest();
    var index = getIndexName(INSTANCE_SUBJECT_RESOURCE, tenant);
    var fetchSourceContext = new FetchSourceContext(false);
    documents.stream()
      .map(SearchDocumentBody::getId)
      .distinct()
      .map(documentId -> new Item(index, documentId).routing(tenant).fetchSourceContext(fetchSourceContext))
      .forEach(multiGetRequest::add);
    return multiGetRequest;
  }

  private int getRetryAttempts() {
    return Optional.ofNullable(searchConfigurationProperties)
      .map(SearchConfigurationProperties::getIndexing)
      .map(IndexingSettings::getInstanceSubjects)
      .map(InstanceSubjectsIndexingSettings::getRetryAttempts)
      .orElse(3);
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
    Map<String, SearchDocumentBody> searchDocumentBySubject, Map<String, GetResponse> esDocumentsById) {
    var doc = searchDocumentBySubject.get(subject);
    var esDocument = esDocumentsById.get(doc.getId());
    return esDocument != null ? prepareDeleteRequest(doc, esDocument) : null;
  }

  private static DeleteRequest prepareDeleteRequest(SearchDocumentBody doc, GetResponse esDocument) {
    return new DeleteRequest(doc.getIndex())
      .id(doc.getId())
      .routing(doc.getRouting())
      .setIfSeqNo(esDocument.getSeqNo())
      .setIfPrimaryTerm(esDocument.getPrimaryTerm());
  }

  private static List<String> getFailedDocumentIds(BulkResponse bulkResponse) {
    return Arrays.stream(bulkResponse.getItems())
      .filter(BulkItemResponse::isFailed)
      .map(BulkItemResponse::getFailure)
      .map(Failure::getId)
      .collect(toList());
  }
}
