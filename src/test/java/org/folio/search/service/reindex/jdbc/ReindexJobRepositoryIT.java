package org.folio.search.service.reindex.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.mockito.Mockito.when;

import java.util.UUID;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@IntegrationTest
@JdbcTest
@EnablePostgres
@AutoConfigureJson
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReindexJobRepositoryIT {

  private @Autowired JdbcTemplate jdbcTemplate;
  private @MockitoBean FolioExecutionContext context;
  private ReindexJobRepository repository;

  @BeforeEach
  void setUp() {
    repository = new ReindexJobRepository(context, jdbcTemplate);
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
  void findActiveJobId_returnsEmpty_whenNoJobRegistered() {
    assertThat(repository.findActiveJobId()).isEmpty();
  }

  @Test
  void replaceActiveJob_keepsOnlyLatestJob() {
    var firstJobId = UUID.randomUUID();
    var secondJobId = UUID.randomUUID();

    repository.replaceActiveJob(firstJobId);
    assertThat(repository.findActiveJobId()).contains(firstJobId);

    repository.replaceActiveJob(secondJobId);
    assertThat(repository.findActiveJobId()).contains(secondJobId);
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public.reindex_job", Integer.class)).isOne();
  }
}
