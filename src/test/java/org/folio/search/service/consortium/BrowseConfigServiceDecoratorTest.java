package org.folio.search.service.consortium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.domain.dto.BrowseType.INSTANCE_CLASSIFICATION;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.folio.search.domain.dto.BrowseConfig;
import org.folio.search.domain.dto.BrowseConfigCollection;
import org.folio.search.domain.dto.BrowseOptionType;
import org.folio.search.service.config.BrowseConfigService;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class BrowseConfigServiceDecoratorTest extends DecoratorBaseTest {

  private @Mock ConsortiumTenantExecutor consortiumTenantExecutor;
  private @Mock BrowseConfigService service;
  private @InjectMocks BrowseConfigServiceDecorator decorator;

  @Test
  void getConfigs() {
    var expected = new BrowseConfigCollection();
    when(service.getConfigs(INSTANCE_CLASSIFICATION)).thenReturn(expected);
    mockExecutor(consortiumTenantExecutor);

    var actual = decorator.getConfigs(INSTANCE_CLASSIFICATION);

    assertThat(actual).isEqualTo(expected);
    verify(service).getConfigs(INSTANCE_CLASSIFICATION);
    verify(consortiumTenantExecutor).execute(any());
  }

  @Test
  void upsertConfig() {
    var config = new BrowseConfig();
    doNothing().when(service).upsertConfig(INSTANCE_CLASSIFICATION, BrowseOptionType.LC, config);
    mockExecutorRun(consortiumTenantExecutor);

    decorator.upsertConfig(INSTANCE_CLASSIFICATION, BrowseOptionType.LC, config);

    verify(service).upsertConfig(INSTANCE_CLASSIFICATION, BrowseOptionType.LC, config);
    verify(consortiumTenantExecutor).run(any());
  }
}
