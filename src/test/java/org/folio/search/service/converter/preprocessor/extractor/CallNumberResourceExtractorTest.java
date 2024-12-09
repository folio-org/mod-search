package org.folio.search.service.converter.preprocessor.extractor;

import static org.folio.search.service.converter.preprocessor.extractor.impl.CallNumberResourceExtractor.CALL_NUMBER_FIELD;
import static org.folio.search.service.converter.preprocessor.extractor.impl.CallNumberResourceExtractor.CHRONOLOGY_FIELD;
import static org.folio.search.service.converter.preprocessor.extractor.impl.CallNumberResourceExtractor.COPY_NUMBER_FIELD;
import static org.folio.search.service.converter.preprocessor.extractor.impl.CallNumberResourceExtractor.EFFECTIVE_CALL_NUMBER_COMPONENTS_FIELD;
import static org.folio.search.service.converter.preprocessor.extractor.impl.CallNumberResourceExtractor.ENUMERATION_FIELD;
import static org.folio.search.service.converter.preprocessor.extractor.impl.CallNumberResourceExtractor.PREFIX_FIELD;
import static org.folio.search.service.converter.preprocessor.extractor.impl.CallNumberResourceExtractor.SUFFIX_FIELD;
import static org.folio.search.service.converter.preprocessor.extractor.impl.CallNumberResourceExtractor.TYPE_ID_FIELD;
import static org.folio.search.service.converter.preprocessor.extractor.impl.CallNumberResourceExtractor.VOLUME_FIELD;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.function.Supplier;
import org.folio.search.domain.dto.TenantConfiguredFeature;
import org.folio.search.service.FeatureConfigService;
import org.folio.search.service.converter.preprocessor.extractor.impl.CallNumberResourceExtractor;
import org.folio.search.service.reindex.jdbc.CallNumberRepository;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class CallNumberResourceExtractorTest extends ChildResourceExtractorTestBase {

  @Mock
  private CallNumberRepository repository;
  @Mock
  private FeatureConfigService featureConfigService;

  private CallNumberResourceExtractor extractor;

  @Override
  protected int getExpectedEntitiesSize() {
    return 1;
  }

  @BeforeEach
  void setUp() {
    extractor = new CallNumberResourceExtractor(repository,
      new JsonConverter(new ObjectMapper()),
      featureConfigService);
  }

  @Test
  void persistChildren() {
    when(featureConfigService.isEnabled(TenantConfiguredFeature.BROWSE_CALL_NUMBERS)).thenReturn(true);
    persistChildrenTest(extractor, repository, callNumberBodySupplier());
  }

  private static Supplier<Map<String, Object>> callNumberBodySupplier() {
    return () -> mapOf(EFFECTIVE_CALL_NUMBER_COMPONENTS_FIELD, mapOf(
        CALL_NUMBER_FIELD, "call-number",
        SUFFIX_FIELD, "suffix",
        PREFIX_FIELD, "prefix",
        TYPE_ID_FIELD, "type-id"
      ), VOLUME_FIELD, "volume",
      CHRONOLOGY_FIELD, "chronology",
      ENUMERATION_FIELD, "enumeration",
      COPY_NUMBER_FIELD, "copy-number"
    );
  }
}
