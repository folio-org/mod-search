package org.folio.search.service.converter.preprocessor.extractor;

import static org.folio.search.utils.SearchUtils.AUTHORITY_ID_FIELD;
import static org.folio.search.utils.SearchUtils.CONTRIBUTORS_FIELD;
import static org.folio.search.utils.SearchUtils.SUBJECT_SOURCE_ID_FIELD;
import static org.folio.search.utils.SearchUtils.SUBJECT_TYPE_ID_FIELD;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.apache.commons.lang3.RandomStringUtils;
import org.folio.search.service.converter.preprocessor.extractor.impl.ContributorResourceExtractor;
import org.folio.search.service.reindex.jdbc.ContributorRepository;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ContributorResourceExtractorTest extends ChildResourceExtractorTestBase {

  @Mock
  private JsonConverter jsonConverter;
  @Mock
  private ContributorRepository repository;

  @InjectMocks
  private ContributorResourceExtractor extractor;

  @Test
  void persistChildren() {
    persistChildrenTest(extractor, repository, contributorsBodySupplier());
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
}
