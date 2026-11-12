package org.folio.search.service.indexrebuild.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.exception.RequestValidationException;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ItemRecordsRebuildStrategyTest {

  @Mock
  private ItemSharedFlagResolver sharedFlagResolver;
  private SearchConfigurationProperties searchConfiguration;
  private ItemRecordsRebuildStrategy strategy;

  @BeforeEach
  void setUp() {
    searchConfiguration = new SearchConfigurationProperties();
    searchConfiguration.setIndexing(new SearchConfigurationProperties.IndexingSettings());
    strategy = new ItemRecordsRebuildStrategy(sharedFlagResolver, searchConfiguration);
  }

  @Test
  void validateEnabled_positive() {
    searchConfiguration.getIndexing().setItemIndexEnabled(true);

    assertThatCode(() -> strategy.validateEnabled()).doesNotThrowAnyException();
  }

  @Test
  void validateEnabled_negative_itemIndexDisabled() {
    searchConfiguration.getIndexing().setItemIndexEnabled(false);

    assertThatThrownBy(() -> strategy.validateEnabled()).isInstanceOf(RequestValidationException.class);
  }

  @Test
  void documentId_positive_scopedByTenant() {
    assertThat(strategy.documentId("item", TENANT_ID)).isEqualTo("item|" + TENANT_ID);
  }

  @Test
  void prepare_positive_marksSharedWithoutModifyingRecord() {
    var source = new HashMap<String, Object>(Map.of("id", "item"));
    when(sharedFlagResolver.isShared(TENANT_ID, source)).thenReturn(true);

    var prepared = strategy.prepare(TENANT_ID, source);

    assertThat(prepared).containsEntry("id", "item").containsEntry("shared", true);
    assertThat(source).doesNotContainKey("shared");
  }

  @Test
  void prepare_positive_localItemIsNotMarked() {
    var source = Map.<String, Object>of("id", "item");

    assertThat(strategy.prepare(TENANT_ID, source)).doesNotContainKey("shared");
  }
}
