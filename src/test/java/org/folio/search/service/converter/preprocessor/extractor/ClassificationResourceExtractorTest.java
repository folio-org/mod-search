package org.folio.search.service.converter.preprocessor.extractor;

import static org.folio.search.utils.SearchUtils.CLASSIFICATIONS_FIELD;
import static org.folio.search.utils.SearchUtils.CLASSIFICATION_NUMBER_FIELD;
import static org.folio.search.utils.SearchUtils.CLASSIFICATION_TYPE_FIELD;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.apache.commons.lang3.RandomStringUtils;
import org.folio.search.service.FeatureConfigService;
import org.folio.search.service.converter.preprocessor.extractor.impl.ClassificationResourceExtractor;
import org.folio.search.service.reindex.jdbc.ClassificationRepository;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ClassificationResourceExtractorTest extends ChildResourceExtractorTestBase {

  @Mock
  private JsonConverter jsonConverter;
  @Mock
  private FeatureConfigService featureConfigService;
  @Mock
  private ClassificationRepository repository;

  @InjectMocks
  private ClassificationResourceExtractor extractor;

  @Test
  void persistChildren() {
    persistChildrenTest(extractor, repository, classificationsBodySupplier());
  }

  private static Supplier<Map<String, Object>> classificationsBodySupplier() {
    return () -> Map.of(CLASSIFICATIONS_FIELD, List.of(Map.of(
      CLASSIFICATION_NUMBER_FIELD, RandomStringUtils.insecure().nextAlphanumeric(55),
      CLASSIFICATION_TYPE_FIELD, UUID.randomUUID().toString()
    )));
  }
}
