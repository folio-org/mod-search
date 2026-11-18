package org.folio.search.service.indexrebuild.records;

import static org.folio.search.configuration.RetryTemplateConfiguration.REINDEX_S3_READ_RETRY_TEMPLATE_NAME;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.s3.client.FolioS3Client;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.utils.JsonConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;

/**
 * Reads an exported file of records from the remote storage in batches. The storage client exists only in the EXPORT
 * reindex mode, which is the only mode in which files are delivered.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class RecordsFileReader {

  private final ObjectProvider<FolioS3Client> folioS3Client;
  private final ReindexConfigurationProperties reindexConfig;
  private final JsonConverter jsonConverter;
  @Qualifier(REINDEX_S3_READ_RETRY_TEMPLATE_NAME)
  private final RetryTemplate retryTemplate;

  public void read(String objectKey, Consumer<List<Map<String, Object>>> batchConsumer)
    throws IOException, RetryException {
    var batchSize = reindexConfig.getMergeExportBatchSize();
    var total = 0;
    log.debug("read:: reading file [objectKey: {}]", objectKey);
    try (var stream = retryTemplate.execute(() -> folioS3Client.getObject().read(objectKey));
         var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      var batch = new ArrayList<Map<String, Object>>(batchSize);
      String line;
      while ((line = reader.readLine()) != null) {
        if (!line.isBlank()) {
          batch.add(jsonConverter.fromJsonToMap(line));
          total++;
        }
        if (batch.size() >= batchSize) {
          batchConsumer.accept(List.copyOf(batch));
          batch.clear();
        }
      }
      batchConsumer.accept(List.copyOf(batch));
    }
    log.debug("read:: file read [objectKey: {}, records: {}]", objectKey, total);
  }
}
