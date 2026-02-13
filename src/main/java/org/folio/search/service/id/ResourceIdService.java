package org.folio.search.service.id;

import static java.lang.String.format;
import static org.opensearch.search.sort.SortBuilders.fieldSort;

import java.io.OutputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.configuration.properties.StreamIdsProperties;
import org.folio.search.cql.CqlSearchQueryConverter;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.model.service.CqlResourceIdsRequest;
import org.folio.search.model.streamids.ResourceIdsJobEntity;
import org.folio.search.model.types.StreamJobStatus;
import org.folio.search.repository.ResourceIdsJobRepository;
import org.folio.search.repository.ResourceIdsTemporaryRepository;
import org.folio.search.repository.SearchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ObjectMapper;

@Log4j2
@Service
@RequiredArgsConstructor
public class ResourceIdService {

  private static final String ID_PROPERTY = "id";
  private static final String IDS_PROPERTY = "ids";
  private static final String TOTAL_RECORDS_PROPERTY = "totalRecords";

  private final StreamIdsProperties streamIdsProperties;
  private final ObjectMapper objectMapper;
  private final SearchRepository searchRepository;
  private final CqlSearchQueryConverter queryConverter;
  private final ResourceIdsJobRepository jobRepository;
  private final ResourceIdsTemporaryRepository idsTemporaryRepository;

  /**
   * Streams resource IDs as JSON from the database for a given job.
   * The job must be completed and have IDs prepared in a temporary table.
   *
   * @param jobId        the ID of the async job with the prepared query
   * @param outputStream the output stream to write the JSON data to
   */
  @Transactional
  public void streamResourceIdsAsJson(String jobId, OutputStream outputStream) {
    log.debug("streamIdsFromDatabaseAsJson:: by [jobId: {}]", jobId);

    var job = jobRepository.getReferenceById(jobId);
    if (!job.getStatus().equals(StreamJobStatus.COMPLETED)) {
      throw new SearchServiceException(
        format("Completed async job with query=[%s] was not found.", job.getQuery()));
    }
    processStreamToJson(outputStream, (json, counter) ->
      idsTemporaryRepository.streamIds(job.getTemporaryTableName(), resultSet -> {
        try {
          writeIdObject(json, resultSet);
          counter.incrementAndGet();
        } catch (JacksonException e) {
          throw new SearchServiceException(
            format("Failed to write id value into json stream [reason: %s]", e.getMessage()), e);
        }
      }));
    job.setStatus(StreamJobStatus.DEPRECATED);
    log.info("streamIdsFromDatabaseAsJson:: Attempting to save [job: {}]", job);
    jobRepository.save(job);
    idsTemporaryRepository.dropTableForIds(job.getTemporaryTableName());
  }

  /**
   * Starts job to prepare a list of ids by cql in new DB's table.
   *
   * @param job      Async job as {@link ResourceIdsJobEntity} object
   * @param tenantId tenant id as {@link String} object
   */
  @Transactional
  public void processResourceIdsJob(ResourceIdsJobEntity job, String tenantId) {
    log.debug("streamResourceIdsForJob:: by [job: {}, tenantId: {}]", job, tenantId);
    var tableName = job.getTemporaryTableName();
    try {
      var entityType = job.getEntityType();
      var resource = entityType.getResource();
      var sourceIdPath = entityType.getSourceIdPath();
      var request = new CqlResourceIdsRequest(resource, tenantId, job.getQuery(), sourceIdPath);

      log.info("streamResourceIdsForJob:: Attempting to create table for ids [tableName: {}]", tableName);
      idsTemporaryRepository.createTableForIds(tableName);
      streamIdsFromSearch(request, idsList -> idsTemporaryRepository.insertIds(idsList, tableName));
      job.setStatus(StreamJobStatus.COMPLETED);
    } catch (Exception e) {
      log.warn("Failed to process resource ids job with id = {}, msg: {}", job.getId(), e.getMessage());
      idsTemporaryRepository.dropTableForIds(tableName);
      job.setStatus(StreamJobStatus.ERROR);
    } finally {
      log.info("streamResourceIdsForJob:: Attempts to save [job: {}]", job);
      jobRepository.save(job);
    }
  }

  private void writeIdObject(JsonGenerator json, ResultSet resultSet) throws SQLException {
    json.writeStartObject();
    json.writeStringProperty(ID_PROPERTY, resultSet.getString(1));
    json.writeEndObject();
  }

  private void streamIdsFromSearch(CqlResourceIdsRequest request, Consumer<List<String>> idsConsumer) {
    log.info("streamResourceIds:: by [query: {}, resource: {}]", request.query(), request.resource());

    var searchSource = queryConverter
      .convertForConsortia(request.query(), request.resource(), request.tenantId())
      .size(streamIdsProperties.getScrollQuerySize())
      .fetchSource(new String[] {request.sourceFieldPath()}, null)
      .sort(fieldSort("_doc"));

    searchRepository.streamResourceIds(request, searchSource, idsConsumer);
  }

  private void processStreamToJson(OutputStream outputStream,
                                   BiConsumer<JsonGenerator, AtomicInteger> idsStreamProcessor) {
    try (var json = objectMapper.createGenerator(outputStream)) {
      json.writeStartObject();
      json.writeArrayPropertyStart(IDS_PROPERTY);

      var totalRecordsCounter = new AtomicInteger();
      idsStreamProcessor.accept(json, totalRecordsCounter);

      json.writeEndArray();
      json.writeNumberProperty(TOTAL_RECORDS_PROPERTY, totalRecordsCounter.get());
      json.writeEndObject();
      json.flush();
    } catch (JacksonException e) {
      throw new SearchServiceException(
        format("Failed to write data into json [reason: %s]", e.getMessage()), e);
    }
  }
}
