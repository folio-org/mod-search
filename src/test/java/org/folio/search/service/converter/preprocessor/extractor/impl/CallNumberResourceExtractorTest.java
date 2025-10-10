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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.function.Supplier;
import org.folio.search.domain.dto.TenantConfiguredFeature;
import org.folio.search.service.FeatureConfigService;
import org.folio.search.service.consortium.ConsortiumTenantProvider;
import org.folio.search.service.reindex.jdbc.CallNumberRepository;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.testing.type.UnitTest;
import org.folio.support.base.ChildResourceExtractorTestBase;
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
  private FeatureConfigService configService;
  @Mock
  private ConsortiumTenantProvider tenantProvider;

  private CallNumberResourceExtractor extractor;

  @Override
  protected int getExpectedEntitiesSize() {
    return 1;
  }

  @BeforeEach
  void setUp() {
    extractor = new CallNumberResourceExtractor(repository, new JsonConverter(new ObjectMapper()), configService);
  }

  @Test
  void persistChildren() {
    when(configService.isEnabled(TenantConfiguredFeature.BROWSE_CALL_NUMBERS)).thenReturn(true);
    persistChildrenTest(extractor, repository, callNumberBodySupplier());
    verify(repository, times(2)).deleteByInstanceIds(anyList(), eq(TENANT_ID));
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
      )
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
