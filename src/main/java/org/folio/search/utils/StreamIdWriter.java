package org.folio.search.utils;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import lombok.experimental.UtilityClass;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.search.exception.SearchServiceException;

/**
 * Shared utilities for streaming resource IDs to output streams.
 * Used by both ResourceIdService and VersionedResourceIdService.
 */
@UtilityClass
public class StreamIdWriter {

  public static OutputStreamWriter createOutputStreamWriter(OutputStream outputStream) {
    return new OutputStreamWriter(outputStream, UTF_8);
  }

  public static void writeRecordIdsToText(List<String> recordIds, OutputStreamWriter writer) {
    if (CollectionUtils.isEmpty(recordIds)) {
      return;
    }

    try {
      for (var recordId : recordIds) {
        writer.write(recordId + '\n');
      }
      writer.flush();
    } catch (IOException e) {
      throw new SearchServiceException(
        format("Failed to write id value into output stream [reason: %s]", e.getMessage()), e);
    }
  }

  public static void writeRecordIdsToJson(List<String> recordIds, JsonGenerator json) {
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
        format("Failed to write id value into json stream [reason: %s]", e.getMessage()), e);
    }
  }

  public static void processStreamToJson(ObjectMapper objectMapper, OutputStream outputStream,
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
}
