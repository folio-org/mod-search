package org.folio.search.service.reindex.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.mockito.Mockito.when;

import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.service.consortium.ConsortiumTenantService;
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
class UploadRangeRepositoriesIT {

  private @Autowired JdbcTemplate jdbcTemplate;
  private @MockitoBean FolioExecutionContext context;
  private @MockitoBean ReindexConfigurationProperties reindexConfig;
  private @MockitoBean ConsortiumTenantService consortiumTenantService;
  private UploadInstanceRepository uploadRepository;

  @BeforeEach
  void setUp() {
    var jsonConverter = new JsonConverter(new JsonMapper());
    uploadRepository = new UploadInstanceRepository(jdbcTemplate, jsonConverter, context,
      reindexConfig, consortiumTenantService);
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

    // assert
    assertThat(uploadRanges)
      .hasSize(1)
      .allMatch(range -> range.getStatus() == null && range.getFailCause() == null);
  }
}
