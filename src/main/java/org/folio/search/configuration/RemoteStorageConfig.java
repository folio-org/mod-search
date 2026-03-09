package org.folio.search.configuration;

import static org.folio.search.utils.LogUtils.hideIfSet;

import lombok.extern.log4j.Log4j2;
import org.folio.s3.client.FolioS3Client;
import org.folio.s3.client.S3ClientFactory;
import org.folio.s3.client.S3ClientProperties;
import org.folio.s3.exception.S3ClientException;
import org.folio.search.configuration.properties.RemoteStorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Log4j2
@Configuration
@ConditionalOnProperty(name = "folio.reindex.reindex-type", havingValue = "EXPORT")
public class RemoteStorageConfig {

  @Bean
  public FolioS3Client remoteFolioS3Client(RemoteStorageProperties properties) {
    log.info("Configure remote storage [endpoint={}, region={}, bucket={}, accessKey={}, secretKey={}, awsSdk={}]",
      properties.getEndpoint(), properties.getRegion(), properties.getBucket(),
      hideIfSet(properties.getAccessKey()),
      hideIfSet(properties.getSecretKey()),
      properties.isAwsSdk());
    try {
      var client = S3ClientFactory.getS3Client(S3ClientProperties.builder()
        .endpoint(properties.getEndpoint())
        .secretKey(properties.getSecretKey())
        .accessKey(properties.getAccessKey())
        .bucket(properties.getBucket())
        .region(properties.getRegion())
        .awsSdk(properties.isAwsSdk())
        .build());
      client.createBucketIfNotExists();
      return client;
    } catch (S3ClientException e) {
      log.error("Error creating bucket: {} during RemoteStorageClient initialization", properties.getBucket(), e);
      throw e;
    }
  }
}
