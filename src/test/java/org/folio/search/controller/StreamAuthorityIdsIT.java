package org.folio.search.controller;

import static org.folio.search.sample.SampleAuthorities.getAuthoritySampleAsMap;
import static org.folio.search.support.base.ApiEndpoints.authorityIdsJob;

import lombok.SneakyThrows;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.StreamIdsJob;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.search.utils.types.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@IntegrationTest
class StreamAuthorityIdsIT extends BaseIntegrationTest {

  @BeforeAll
  static void prepare() {
    setUpTenant(Authority.class, 21, getAuthoritySampleAsMap());
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @SneakyThrows
  @Test
  void createJob() {
    doPost(authorityIdsJob(), new StreamIdsJob().query("id=*"));
  }

}
