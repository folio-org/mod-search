package org.folio.search.service.reindex.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.mockito.Mockito.when;

import org.assertj.core.api.Condition;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.model.reindex.UploadRangeEntity;
import org.folio.search.model.types.ReindexEntityType;
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
class SubjectJdbcRepositoryIT {

  private @Autowired JdbcTemplate jdbcTemplate;
  private @MockBean FolioExecutionContext context;
  private SubjectJdbcRepository repository;
  private ReindexConfigurationProperties properties;

  @BeforeEach
  void setUp() {
    properties = new ReindexConfigurationProperties();
    repository = new SubjectJdbcRepository(jdbcTemplate, context, properties);
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
  void getUploadRanges_returnEmptyList_whenNoUploadRangesAndNotPopulate() {
    // act
    var ranges = repository.getUploadRanges(false);

    // assert
    assertThat(ranges).isEmpty();
  }

  @Test
  @Sql("/sql/populate-subjects.sql")
  void getUploadRanges_returnList_whenNoUploadRangesAndNotPopulate() {
    // arrange
    properties.setUploadRangeSize(5);

    // act
    var ranges = repository.getUploadRanges(true);

    // assert
    assertThat(ranges)
      .hasSize(5)
      .are(new Condition<>(range -> range.getEntityType() == ReindexEntityType.SUBJECT, "subject range"))
      .extracting(UploadRangeEntity::getLimit, UploadRangeEntity::getOffset)
      .containsExactly(tuple(5, 0), tuple(5, 5), tuple(5, 10), tuple(5, 15), tuple(1, 20));
  }
}
