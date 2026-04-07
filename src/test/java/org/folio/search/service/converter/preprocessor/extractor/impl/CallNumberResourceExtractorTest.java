package org.folio.search.service.converter.preprocessor.extractor.impl;

import static org.folio.search.service.converter.preprocessor.extractor.impl.CallNumberResourceExtractor.CALL_NUMBER_FIELD;
import static org.folio.search.service.converter.preprocessor.extractor.impl.CallNumberResourceExtractor.EFFECTIVE_CALL_NUMBER_COMPONENTS_FIELD;
import static org.folio.search.service.converter.preprocessor.extractor.impl.CallNumberResourceExtractor.PREFIX_FIELD;
import static org.folio.search.service.converter.preprocessor.extractor.impl.CallNumberResourceExtractor.SUFFIX_FIELD;
import static org.folio.search.service.converter.preprocessor.extractor.impl.CallNumberResourceExtractor.TYPE_ID_FIELD;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.utils.TestUtils.mapOf;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.function.Supplier;
import org.folio.search.domain.dto.TenantConfiguredFeature;
import org.folio.search.service.FeatureConfigService;
import org.folio.search.service.reindex.jdbc.CallNumberRepository;
import org.folio.spring.testing.type.UnitTest;
import org.folio.support.base.ChildResourceExtractorTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class CallNumberResourceExtractorTest extends ChildResourceExtractorTestBase {

  @Mock
  private CallNumberRepository repository;
  @Mock
  private FeatureConfigService configService;

  @InjectMocks
  private CallNumberResourceExtractor extractor;

  @Override
  protected int getExpectedEntitiesSize() {
    return 1;
  }

  @Test
  void persistChildren() {
    when(configService.isEnabled(TenantConfiguredFeature.BROWSE_CALL_NUMBERS)).thenReturn(true);
    persistChildrenTest(extractor, repository, callNumberBodySupplier());
    verify(repository, times(1)).deleteByInstanceIds(anyList(), eq(TENANT_ID));
  }

  @Test
  void persistChildrenOnReindex() {
    when(configService.isEnabled(TenantConfiguredFeature.BROWSE_CALL_NUMBERS)).thenReturn(true);
    persistChildrenOnReindexTest(extractor, repository, callNumberBodySupplier());
  }

  @Test
  void shouldNotPersistEmptyCallNumber() {
    when(configService.isEnabled(TenantConfiguredFeature.BROWSE_CALL_NUMBERS)).thenReturn(true);
    shouldNotPersistEmptyChildrenTest(extractor, repository, emptyCallNumberBodySupplier());
  }

  private static Supplier<Map<String, Object>> callNumberBodySupplier() {
    return () -> Map.of("resource", "item",
      "body", mapOf(EFFECTIVE_CALL_NUMBER_COMPONENTS_FIELD, mapOf(
        CALL_NUMBER_FIELD, "call-number",
        SUFFIX_FIELD, "suffix",
        PREFIX_FIELD, "prefix",
        TYPE_ID_FIELD, "type-id"
        ),
        "id", "id",
        "instanceId", "instance-id",
        "effectiveLocationId", "location-id"
    ));
  }

  private static Supplier<Map<String, Object>> emptyCallNumberBodySupplier() {
    return () -> mapOf(EFFECTIVE_CALL_NUMBER_COMPONENTS_FIELD, mapOf(
        CALL_NUMBER_FIELD, "        ",
        SUFFIX_FIELD, "suffix",
        PREFIX_FIELD, "prefix",
        TYPE_ID_FIELD, "type-id"
      )
    );
  }
}
