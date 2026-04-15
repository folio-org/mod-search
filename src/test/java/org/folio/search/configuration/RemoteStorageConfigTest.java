package org.folio.search.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import org.folio.s3.client.FolioS3Client;
import org.folio.s3.client.S3ClientFactory;
import org.folio.s3.client.S3ClientProperties;
import org.folio.s3.exception.S3ClientException;
import org.folio.search.configuration.properties.RemoteStorageProperties;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class RemoteStorageConfigTest {

  private static final String ENDPOINT = "https://s3.example.com";
  private static final String REGION = "us-east-1";
  private static final String BUCKET = "test-bucket";
  private static final String ACCESS_KEY = "access-key";
  private static final String SECRET_KEY = "secret-key";

  private final RemoteStorageConfig remoteStorageConfig = new RemoteStorageConfig();

  @Test
  void remoteFolioS3Client_positive_returnsClientAndCreatesBucket() {
    var remoteStorageProperties = remoteStorageProperties();
    var s3ClientProperties = s3ClientProperties();
    var folioS3Client = mock(FolioS3Client.class);
    try (var s3ClientFactory = mockStatic(S3ClientFactory.class)) {
      s3ClientFactory.when(() -> S3ClientFactory.getS3Client(s3ClientProperties)).thenReturn(folioS3Client);

      var result = remoteStorageConfig.remoteFolioS3Client(remoteStorageProperties);

      assertThat(result).isSameAs(folioS3Client);
      verify(folioS3Client).createBucketIfNotExists();
      s3ClientFactory.verify(() -> S3ClientFactory.getS3Client(s3ClientProperties));
    }
  }

  @Test
  void remoteFolioS3Client_negative_s3ClientExceptionIsPropagated() {
    var remoteStorageProperties = remoteStorageProperties();
    var s3ClientProperties = s3ClientProperties();
    var expectedException = new S3ClientException("Failed to create client");
    try (var s3ClientFactory = mockStatic(S3ClientFactory.class)) {
      s3ClientFactory.when(() -> S3ClientFactory.getS3Client(s3ClientProperties)).thenThrow(expectedException);

      assertThatThrownBy(() -> remoteStorageConfig.remoteFolioS3Client(remoteStorageProperties))
        .isSameAs(expectedException);
      s3ClientFactory.verify(() -> S3ClientFactory.getS3Client(s3ClientProperties));
    }
  }

  private static RemoteStorageProperties remoteStorageProperties() {
    var remoteStorageProperties = new RemoteStorageProperties();
    remoteStorageProperties.setEndpoint(ENDPOINT);
    remoteStorageProperties.setRegion(REGION);
    remoteStorageProperties.setBucket(BUCKET);
    remoteStorageProperties.setAccessKey(ACCESS_KEY);
    remoteStorageProperties.setSecretKey(SECRET_KEY);
    remoteStorageProperties.setAwsSdk(true);
    return remoteStorageProperties;
  }

  private static S3ClientProperties s3ClientProperties() {
    return S3ClientProperties.builder()
      .endpoint(ENDPOINT)
      .secretKey(SECRET_KEY)
      .accessKey(ACCESS_KEY)
      .bucket(BUCKET)
      .region(REGION)
      .awsSdk(true)
      .build();
  }
}
