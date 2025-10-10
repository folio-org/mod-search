package org.folio.search.service.converter.preprocessor.extractor;

import static org.folio.search.utils.SearchUtils.AUTHORITY_ID_FIELD;
import static org.folio.search.utils.SearchUtils.SUBJECTS_FIELD;
import static org.folio.search.utils.SearchUtils.SUBJECT_SOURCE_ID_FIELD;
import static org.folio.search.utils.SearchUtils.SUBJECT_TYPE_ID_FIELD;
import static org.folio.search.utils.SearchUtils.SUBJECT_VALUE_FIELD;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.apache.commons.lang3.RandomStringUtils;
import org.folio.search.service.converter.preprocessor.extractor.impl.SubjectResourceExtractor;
import org.folio.search.service.reindex.jdbc.SubjectRepository;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class SubjectResourceExtractorTest extends ChildResourceExtractorTestBase {

  @Mock
  private JsonConverter jsonConverter;
  @Mock
  private SubjectRepository repository;

  @InjectMocks
  private SubjectResourceExtractor extractor;

  @Test
  void persistChildren() {
    persistChildrenTest(extractor, repository, subjectsBodySupplier());
  }

  private static Supplier<Map<String, Object>> subjectsBodySupplier() {
    return () -> Map.of("resource", "instance",
      "body", Map.of(SUBJECTS_FIELD, List.of(Map.of(
      SUBJECT_VALUE_FIELD, RandomStringUtils.insecure().nextAlphanumeric(260),
      AUTHORITY_ID_FIELD, UUID.randomUUID().toString(),
      SUBJECT_SOURCE_ID_FIELD, UUID.randomUUID().toString(),
      SUBJECT_TYPE_ID_FIELD, UUID.randomUUID().toString()
    ))));
  }
}
