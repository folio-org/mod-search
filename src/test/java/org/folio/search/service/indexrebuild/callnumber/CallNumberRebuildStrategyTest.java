package org.folio.search.service.indexrebuild.callnumber;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.domain.dto.TenantConfiguredFeature;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.service.FeatureConfigService;
import org.folio.search.service.indexrebuild.IndexRebuildTarget;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.index.query.NestedQueryBuilder;

@UnitTest
@ExtendWith(MockitoExtension.class)
class CallNumberRebuildStrategyTest {

  @Mock
  private FeatureConfigService featureConfigService;
  private SearchConfigurationProperties searchConfiguration;
  private CallNumberRebuildStrategy strategy;

  @BeforeEach
  void setUp() {
    searchConfiguration = new SearchConfigurationProperties();
    searchConfiguration.setIndexing(new SearchConfigurationProperties.IndexingSettings());
    strategy = new CallNumberRebuildStrategy(featureConfigService, searchConfiguration);
  }

  @Test
  void targets_positive_derivedFromItemIndex() {
    assertThat(strategy.target()).isEqualTo(IndexRebuildTarget.CALL_NUMBER);
    assertThat(strategy.source()).isEqualTo(IndexRebuildTarget.ITEM);
    assertThat(strategy.keyField()).isEqualTo("callNumberId");
    assertThat(strategy.documentKeyField()).isEqualTo("id");
  }

  @Test
  void validateEnabled_negative_browseCallNumbersDisabled() {
    when(featureConfigService.isEnabled(TenantConfiguredFeature.BROWSE_CALL_NUMBERS)).thenReturn(false);

    assertThatThrownBy(() -> strategy.validateEnabled()).isInstanceOf(RequestValidationException.class)
      .hasMessage("Browsing call numbers is not enabled");
  }

  @Test
  void isTrackingEnabled_positive_followsIndexFromItemsFlag() {
    searchConfiguration.getIndexing().setCallNumberIndexFromItemsEnabled(true);
    assertThat(strategy.isTrackingEnabled()).isTrue();

    searchConfiguration.getIndexing().setCallNumberIndexFromItemsEnabled(false);
    assertThat(strategy.isTrackingEnabled()).isFalse();
  }

  @Test
  void keysOf_positive_callNumberOfItemRecord() {
    var item = Map.of("effectiveCallNumberComponents", Map.of("callNumber", "A1"));

    assertThat(strategy.keysOf(item)).containsExactly(CallNumberKey.fromItem(item).orElseThrow().id());
    assertThat(strategy.keysOf(Map.of("id", "item"))).isEmpty();
  }

  @Test
  void documentsOfTenantQuery_positive_nestedInstancesTenant() {
    var query = strategy.documentsOfTenantQuery(TENANT_ID);

    assertThat(query).isInstanceOf(NestedQueryBuilder.class);
    assertThat(query.toString()).contains("instances.tenantId", TENANT_ID);
  }
}
