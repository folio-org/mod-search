package org.folio.search.service.reindex.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.testing.extension.EnablePostgres;
import org.folio.spring.testing.type.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@JdbcTest
@EnablePostgres
@AutoConfigureJson
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UploadRangeRepositoriesIT {

  private @Autowired JdbcTemplate jdbcTemplate;
  private @MockBean FolioExecutionContext context;
  private @MockBean ReindexConfigurationProperties reindexConfig;
  private UploadInstanceRepository uploadRepository;

  @BeforeEach
  void setUp() {
    var jsonConverter = new JsonConverter(new ObjectMapper());
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
    when(reindexConfig.getUploadRangeSize()).thenReturn(1);
  }

  @Test
  @Sql({"/sql/populate-instances.sql"})
  void getUploadRanges_shouldNotPopulateStatus() {
    // act
    var uploadRanges = uploadRepository.createUploadRanges();
    System.out.println(uploadRanges.size());

    // assert
    assertThat(uploadRanges)
      .hasSize(1)
      .allMatch(range -> range.getStatus() == null && range.getFailCause() == null);
  }
}
