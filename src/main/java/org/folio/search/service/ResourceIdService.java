package org.folio.search.service;

import static org.elasticsearch.search.sort.SortBuilders.fieldSort;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections.CollectionUtils;
import org.folio.search.configuration.properties.StreamIdsProperties;
import org.folio.search.cql.CqlSearchQueryConverter;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.model.service.CqlResourceIdsRequest;
import org.folio.search.repository.SearchRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ResourceIdService {

  private final StreamIdsProperties streamIdsProperties;
  private final ObjectMapper objectMapper;
  private final SearchRepository searchRepository;
  private final CqlSearchQueryConverter queryConverter;

  /**
   * Returns resource ids for passed cql query.
   *
   * @param request      resource ids request as {@link CqlResourceIdsRequest} object
   * @param outputStream output stream where json will be written in.
   */
  public void streamResourceIds(CqlResourceIdsRequest request, OutputStream outputStream) {
    try (var json = objectMapper.createGenerator(outputStream)) {
      json.writeStartObject();
      json.writeFieldName("ids");
      json.writeStartArray();

      var totalRecordsCounter = new AtomicInteger();
      streamResourceIds(request, ids -> {
        totalRecordsCounter.addAndGet(ids.size());
        writeRecordIdsToOutputStream(ids, json);
      });

      json.writeEndArray();
      json.writeNumberField("totalRecords", totalRecordsCounter.get());
      json.writeEndObject();
      json.flush();
    } catch (IOException e) {
      throw new SearchServiceException(
        String.format("Failed to write data into json [reason: %s]", e.getMessage()), e);
    }
  }

  private void streamResourceIds(CqlResourceIdsRequest request, Consumer<List<String>> idsConsumer) {
    var resource = request.getResource();
    var searchSource = queryConverter.convert(request.getQuery(), resource)
      .size(streamIdsProperties.getScrollQuerySize())
      .fetchSource(new String[] {request.getSourceFieldPath()}, null)
      .sort(List.of(fieldSort("_doc")));

    searchRepository.streamResourceIds(request, searchSource, idsConsumer);
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
        String.format("Failed to write to id value into json stream [reason: %s]", e.getMessage()), e);
    }
  }
}
