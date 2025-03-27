package org.folio.search.service.consortium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;
import org.folio.search.domain.dto.BrowseConfig;
import org.folio.search.domain.dto.BrowseConfigCollection;
import org.folio.search.domain.dto.BrowseOptionType;
import org.folio.search.domain.dto.BrowseType;
import org.folio.search.service.config.BrowseConfigService;
import org.folio.spring.testing.type.UnitTest;
import org.folio.support.base.DecoratorBaseTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class BrowseConfigServiceDecoratorTest extends DecoratorBaseTest {

  private @Mock ConsortiumTenantExecutor consortiumTenantExecutor;
  private @Mock BrowseConfigService service;
  private @InjectMocks BrowseConfigServiceDecorator decorator;

  @ParameterizedTest
  @EnumSource(BrowseType.class)
  void getConfigs(BrowseType type) {
    var expected = new BrowseConfigCollection();
    when(service.getConfigs(type)).thenReturn(expected);
    mockExecutor(consortiumTenantExecutor);

    var actual = decorator.getConfigs(type);

    assertThat(actual).isEqualTo(expected);
    verify(service).getConfigs(type);
    verify(consortiumTenantExecutor).execute(any());
  }

  @ParameterizedTest
  @MethodSource("browseArguments")
  void getConfig(BrowseType browseType, BrowseOptionType optionType) {
    var expected = new BrowseConfig();
    when(service.getConfig(browseType, optionType)).thenReturn(expected);
    mockExecutor(consortiumTenantExecutor);

    var actual = decorator.getConfig(browseType, optionType);

    assertThat(actual).isEqualTo(expected);
    verify(service).getConfig(browseType, optionType);
    verify(consortiumTenantExecutor).execute(any());
  }

  @ParameterizedTest
  @MethodSource("browseArguments")
  void upsertConfig(BrowseType browseType, BrowseOptionType optionType) {
    var config = new BrowseConfig();
    doNothing().when(service).upsertConfig(browseType, optionType, config);
    mockExecutorRun(consortiumTenantExecutor);

    decorator.upsertConfig(browseType, optionType, config);

    verify(service).upsertConfig(browseType, optionType, config);
    verify(consortiumTenantExecutor).run(any());
  }

  private static Stream<Arguments> browseArguments() {
    return Arrays.stream(BrowseType.values())
      .map(browseType -> Arrays.stream(BrowseOptionType.values())
        .map(optionType -> Arguments.of(browseType, optionType))
        .toList())
      .flatMap(Collection::stream);
  }
}
