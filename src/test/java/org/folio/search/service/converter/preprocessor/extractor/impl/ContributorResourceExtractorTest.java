package org.folio.search.service.converter.preprocessor.extractor.impl;

import static org.folio.search.utils.SearchUtils.AUTHORITY_ID_FIELD;
import static org.folio.search.utils.SearchUtils.CONTRIBUTORS_FIELD;
import static org.folio.search.utils.SearchUtils.SUBJECT_SOURCE_ID_FIELD;
import static org.folio.search.utils.SearchUtils.SUBJECT_TYPE_ID_FIELD;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.apache.commons.lang3.RandomStringUtils;
import org.folio.search.domain.dto.TenantConfiguredFeature;
import org.folio.search.service.FeatureConfigService;
import org.folio.search.service.reindex.jdbc.ContributorRepository;
import org.folio.spring.testing.type.UnitTest;
import org.folio.support.base.ChildResourceExtractorTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ContributorResourceExtractorTest extends ChildResourceExtractorTestBase {

  @Mock
  private ContributorRepository repository;
  @Mock
  private FeatureConfigService configService;

  @InjectMocks
  private ContributorResourceExtractor extractor;

  @Test
  void persistChildren() {
    when(configService.isEnabled(TenantConfiguredFeature.BROWSE_CONTRIBUTORS)).thenReturn(true);
    persistChildrenTest(extractor, repository, contributorsBodySupplier());
  }

  @Test
  void shouldNotPersistEmptyContributor() {
    when(configService.isEnabled(TenantConfiguredFeature.BROWSE_CONTRIBUTORS)).thenReturn(true);
    shouldNotPersistEmptyChildrenTest(extractor, repository, emptyContributorsBodySupplier());
  }

  private static Supplier<Map<String, Object>> contributorsBodySupplier() {
    return () -> Map.of("resource", "instance",
      "body", Map.of(CONTRIBUTORS_FIELD, List.of(Map.of(
      "name", RandomStringUtils.insecure().nextAlphanumeric(260),
      AUTHORITY_ID_FIELD, UUID.randomUUID().toString(),
      SUBJECT_SOURCE_ID_FIELD, UUID.randomUUID().toString(),
      SUBJECT_TYPE_ID_FIELD, UUID.randomUUID().toString()
    ))));
  }

  private static Supplier<Map<String, Object>> emptyContributorsBodySupplier() {
    return () -> Map.of(CONTRIBUTORS_FIELD, List.of(Map.of(
      "name", "        ",
      AUTHORITY_ID_FIELD, UUID.randomUUID().toString(),
      SUBJECT_SOURCE_ID_FIELD, UUID.randomUUID().toString(),
      SUBJECT_TYPE_ID_FIELD, UUID.randomUUID().toString()
    )));
  }
}
