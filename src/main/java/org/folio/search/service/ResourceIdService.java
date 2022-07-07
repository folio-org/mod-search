package org.folio.search.service;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.folio.search.model.service.CqlResourceIdsRequest.AUTHORITY_ID_PATH;
import static org.folio.search.utils.SearchUtils.AUTHORITY_RESOURCE;
import static org.opensearch.search.sort.SortBuilders.fieldSort;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections.CollectionUtils;
import org.folio.search.configuration.properties.StreamIdsProperties;
import org.folio.search.cql.CqlSearchQueryConverter;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.model.service.CqlResourceIdsRequest;
import org.folio.search.model.streamids.ResourceIdsJobEntity;
import org.folio.search.model.types.StreamJobStatus;
import org.folio.search.repository.ResourceIdsJobRepository;
import org.folio.search.repository.ResourceIdsTemporaryRepository;
import org.folio.search.repository.SearchRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@RequiredArgsConstructor
public class ResourceIdService {

  private final StreamIdsProperties streamIdsProperties;
  private final ObjectMapper objectMapper;
  private final SearchRepository searchRepository;
  private final CqlSearchQueryConverter queryConverter;
  private final ResourceIdsTemporaryRepository idsTemporaryRepository;
  private final ResourceIdsJobRepository jobRepository;

  /**
   * Returns resource ids for passed cql query in text type.
   *
   * @param request      resource ids request as {@link CqlResourceIdsRequest} object
   * @param outputStream output stream where text will be written in.
   */
  public void streamResourceIdsAsText(CqlResourceIdsRequest request, OutputStream outputStream) {
    var writer = createOutputStreamWriter(outputStream);
    streamResourceIds(request, ids -> writeRecordIdsToOutputStream(ids, writer));
  }

  /**
   * Returns resource ids for passed cql query in json format.
   * Should have a prepared job with ids in the database.
   *
   * @param jobId        async jobs id with prepared query
   * @param outputStream output stream where json will be written in.
   */
  @Transactional
  public void streamIdsFromDatabaseAsJson(String jobId, OutputStream outputStream) {
    var job = jobRepository.getById(jobId);
    if (!job.getStatus().equals(StreamJobStatus.COMPLETED)) {
      throw new SearchServiceException(
        format("Completed async job with query=[%s] was not found.", job.getQuery()));
    }
    processStreamToJson(outputStream, (json, counter) ->
      idsTemporaryRepository.streamIds(job.getTemporaryTableName(), resultSet -> {
        try {
          json.writeStartObject();
          json.writeStringField("id", resultSet.getString(1));
          json.writeEndObject();
          counter.incrementAndGet();
        } catch (IOException e) {
          throw new SearchServiceException(
            format("Failed to write id value into json stream [reason: %s]", e.getMessage()), e);
        }
      }));
    job.setStatus(StreamJobStatus.DEPRECATED);
    jobRepository.save(job);
  }

  /**
   * Starts async job to prepare a list of ids by cql in new DB's table.
   *
   * @param job      Async job as {@link ResourceIdsJobEntity} object
   * @param tenantId tenant id as {@link String} object
   */
  @Async
  @Transactional
  public void streamResourceIdsForJob(ResourceIdsJobEntity job, String tenantId) {
    var tableName = job.getTemporaryTableName();
    try {
      var request = CqlResourceIdsRequest
        .of(AUTHORITY_RESOURCE, tenantId, job.getQuery(), AUTHORITY_ID_PATH);

      idsTemporaryRepository.createTableForIds(tableName);
      streamResourceIds(request, idsList -> idsTemporaryRepository.insertId(idsList, tableName));

      job.setStatus(StreamJobStatus.COMPLETED);
    } catch (Exception e) {
      log.warn("Failed to process resource ids job with id = {}", job.getId());
      idsTemporaryRepository.dropTableForIds(tableName);
      job.setStatus(StreamJobStatus.ERROR);
    } finally {
      jobRepository.save(job);
    }
  }

  /**
   * Returns resource ids for passed cql query in json type.
   *
   * @param request      resource ids request as {@link CqlResourceIdsRequest} object
   * @param outputStream output stream where json will be written in.
   */
  public void streamResourceIdsAsJson(CqlResourceIdsRequest request, OutputStream outputStream) {
    processStreamToJson(outputStream, (json, counter) ->
      streamResourceIds(request, ids -> {
        counter.addAndGet(ids.size());
        writeRecordIdsToOutputStream(ids, json);
      }));
  }

  protected OutputStreamWriter createOutputStreamWriter(OutputStream outputStream) {
    return new OutputStreamWriter(outputStream, UTF_8);
  }

  private void streamResourceIds(CqlResourceIdsRequest request, Consumer<List<String>> idsConsumer) {
    var resource = request.getResource();
    var searchSource = queryConverter.convert(request.getQuery(), resource)
      .size(streamIdsProperties.getScrollQuerySize())
      .fetchSource(new String[] {request.getSourceFieldPath()}, null)
      .sort(fieldSort("_doc"));

    searchRepository.streamResourceIds(request, searchSource, idsConsumer);
  }

  private void processStreamToJson(OutputStream outputStream,
                                   BiConsumer<JsonGenerator, AtomicInteger> idsStreamProcessor) {
    try (var json = objectMapper.createGenerator(outputStream)) {
      json.writeStartObject();
      json.writeFieldName("ids");
      json.writeStartArray();

      var totalRecordsCounter = new AtomicInteger();

      idsStreamProcessor.accept(json, totalRecordsCounter);

      json.writeEndArray();
      json.writeNumberField("totalRecords", totalRecordsCounter.get());
      json.writeEndObject();
      json.flush();
    } catch (IOException e) {
      throw new SearchServiceException(
        format("Failed to write data into json [reason: %s]", e.getMessage()), e);
    }
  }

  private static void writeRecordIdsToOutputStream(List<String> recordIds, JsonGenerator json) {
    if (CollectionUtils.isEmpty(recordIds)) {
      return;
    }

    try {
      for (var recordId : recordIds) {
        json.writeStartObject();
        json.writeStringField("id", recordId);
        json.writeEndObject();
      }
      json.flush();
    } catch (IOException e) {
      throw new SearchServiceException(
        format("Failed to write to id value into json stream [reason: %s]", e.getMessage()), e);
    }
  }

  private static void writeRecordIdsToOutputStream(List<String> recordIds, OutputStreamWriter outputStreamWriter) {
    if (CollectionUtils.isEmpty(recordIds)) {
      return;
    }

    try {
      for (var recordId : recordIds) {
        outputStreamWriter.write(recordId + '\n');
      }
      outputStreamWriter.flush();
    } catch (IOException e) {
      throw new SearchServiceException(
        format("Failed to write id value into output stream [reason: %s]", e.getMessage()), e);
    }
  }

}
