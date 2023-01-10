package org.folio.search.model.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.folio.search.utils.types.UnitTest;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.tools.model.SystemUser;
import org.junit.jupiter.api.Test;

@UnitTest
class FolioExecutionProcessingContextBuilderTest {
  private final FolioExecutionContextBuilder builder =
    new FolioExecutionContextBuilder(mock(FolioModuleMetadata.class));

  @Test
  void canCreateDbOnlyContext() {
    var context = builder.dbOnlyContext("tenant");

    assertThat(context.getTenantId()).isEqualTo("tenant");
    assertThat(context.getFolioModuleMetadata()).isNotNull();
  }

  @Test
  void canCreateSystemUserContext() {
    var systemUser = SystemUser.builder()
      .token("token").username("username")
      .okapiUrl("okapi").tenantId("tenant")
      .build();
    var context = builder.forSystemUser(systemUser);

    assertThat(context.getTenantId()).isEqualTo("tenant");
    assertThat(context.getToken()).isEqualTo("token");
    assertThat(context.getOkapiUrl()).isEqualTo("okapi");

    assertThat(context.getAllHeaders()).isNotNull();
    assertThat(context.getOkapiHeaders()).isNotNull();
    assertThat(context.getFolioModuleMetadata()).isNotNull();
  }
}
