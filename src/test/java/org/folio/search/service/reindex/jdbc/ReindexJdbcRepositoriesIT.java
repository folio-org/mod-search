package org.folio.search.service.reindex.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ReindexRangeStatus;
import org.folio.search.service.consortium.ConsortiumTenantProvider;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.testing.extension.EnablePostgres;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.config.TestNoOpCacheConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
@JdbcTest
@EnablePostgres
@AutoConfigureJson
@Import(TestNoOpCacheConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReindexJdbcRepositoriesIT {

  private @Autowired JdbcTemplate jdbcTemplate;
  private @MockitoBean FolioExecutionContext context;
  private @MockitoBean ReindexConfigurationProperties reindexConfig;
  private @MockitoBean ConsortiumTenantProvider tenantProvider;
  private SearchConfigurationProperties searchConfig;
  private MergeInstanceRepository mergeRepository;
  private UploadInstanceRepository uploadRepository;

  @BeforeEach
  void setUp() {
    var jsonConverter = new JsonConverter(new JsonMapper());
    searchConfig = new SearchConfigurationProperties();
    searchConfig.setIndexing(new SearchConfigurationProperties.IndexingSettings());
    mergeRepository = new MergeInstanceRepository(jdbcTemplate, jsonConverter, context, tenantProvider,
      searchConfig);
    uploadRepository = new UploadInstanceRepository(jdbcTemplate, jsonConverter, context, reindexConfig);
    when(context.getFolioModuleMetadata()).thenReturn(new FolioModuleMetadata() {
      @Override
      public String getModuleName() {
        return null;
      }

      @Override
      public String getDBSchemaName(String tenantId) {
        return "public";
      }
    });
    when(context.getTenantId()).thenReturn(TENANT_ID);
  }

  @Test
  @Sql({"/sql/populate-merge-ranges.sql", "/sql/populate-upload-ranges.sql"})
  void updateRangeStatus() {
    // arrange
    var timestamp = Timestamp.from(Instant.now());
    var failCause = "fail cause";

    // act
    mergeRepository.updateRangeStatus(
      UUID.fromString("9f8febd1-e96c-46c4-a5f4-84a45cc499a2"), timestamp, ReindexRangeStatus.SUCCESS, failCause);
    uploadRepository.updateRangeStatus(
      UUID.fromString("9f8febd1-e96c-46c4-a5f4-84a45cc499a3"), timestamp, ReindexRangeStatus.FAIL, failCause);

    // assert
    var mergeRange = mergeRepository.getMergeRanges().stream()
      .filter(range -> range.getEntityType().equals(ReindexEntityType.INSTANCE))
      .findFirst();
    var uploadRange = uploadRepository.getUploadRanges().stream()
      .filter(range -> range.getEntityType().equals(ReindexEntityType.INSTANCE))
      .findFirst();

    assertThat(mergeRange).isPresent().get()
      .matches(range -> timestamp.getTime() == range.getFinishedAt().getTime()
                        && ReindexRangeStatus.SUCCESS == range.getStatus() && failCause.equals(range.getFailCause()));
    assertThat(uploadRange).isPresent().get()
      .matches(range -> timestamp.getTime() == range.getFinishedAt().getTime()
                        && ReindexRangeStatus.FAIL == range.getStatus() && failCause.equals(range.getFailCause()));
  }
}
