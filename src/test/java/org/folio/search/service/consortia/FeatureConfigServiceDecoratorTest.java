package org.folio.search.service.consortia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.folio.search.domain.dto.FeatureConfig;
import org.folio.search.domain.dto.FeatureConfigs;
import org.folio.search.domain.dto.TenantConfiguredFeature;
import org.folio.search.service.FeatureConfigService;
import org.folio.search.service.consortium.ConsortiumTenantExecutor;
import org.folio.search.service.consortium.FeatureConfigServiceDecorator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeatureConfigServiceDecoratorTest extends DecoratorBaseTest {

  @Mock
  private ConsortiumTenantExecutor consortiumTenantExecutor;
  @Mock
  private FeatureConfigService service;
  @InjectMocks
  private FeatureConfigServiceDecorator decorator;

  @Test
  void isEnabled() {
    var feature = TenantConfiguredFeature.CONSORTIUM;
    when(service.isEnabled(feature)).thenReturn(true);
    mockExecutor(consortiumTenantExecutor);

    var actual = decorator.isEnabled(feature);

    assertThat(actual).isTrue();
    verify(service).isEnabled(feature);
    verify(consortiumTenantExecutor).execute(any());
  }

  @Test
  void getAll() {
    var expected = new FeatureConfigs();
    when(service.getAll()).thenReturn(expected);
    mockExecutor(consortiumTenantExecutor);

    var actual = decorator.getAll();

    assertThat(actual).isEqualTo(expected);
    verify(service).getAll();
    verify(consortiumTenantExecutor).execute(any());
  }

  @Test
  void create() {
    var expected = new FeatureConfig();
    when(service.create(expected)).thenReturn(expected);
    mockExecutor(consortiumTenantExecutor);

    var actual = decorator.create(expected);

    assertThat(actual).isEqualTo(expected);
    verify(service).create(expected);
    verify(consortiumTenantExecutor).execute(any());
  }

  @Test
  void update() {
    var feature = TenantConfiguredFeature.CONSORTIUM;
    var expected = new FeatureConfig();
    when(service.update(feature, expected)).thenReturn(expected);
    mockExecutor(consortiumTenantExecutor);

    var actual = decorator.update(feature, expected);

    assertThat(actual).isEqualTo(expected);
    verify(service).update(feature, expected);
    verify(consortiumTenantExecutor).execute(any());
  }

  @Test
  void delete() {
    var feature = TenantConfiguredFeature.CONSORTIUM;
    mockExecutorRun(consortiumTenantExecutor);

    decorator.delete(feature);

    verify(service).delete(feature);
    verify(consortiumTenantExecutor).run(any());
  }

}
