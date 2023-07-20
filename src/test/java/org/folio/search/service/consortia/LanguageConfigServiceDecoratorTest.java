package org.folio.search.service.consortia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.folio.search.domain.dto.LanguageConfig;
import org.folio.search.domain.dto.LanguageConfigs;
import org.folio.search.service.LanguageConfigService;
import org.folio.search.service.consortium.ConsortiaTenantExecutor;
import org.folio.search.service.consortium.LanguageConfigServiceDecorator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LanguageConfigServiceDecoratorTest extends DecoratorBaseTest {

  @Mock
  private ConsortiaTenantExecutor consortiaTenantExecutor;
  @Mock
  private LanguageConfigService service;
  @InjectMocks
  private LanguageConfigServiceDecorator decorator;

  @Test
  void create() {
    var expected = new LanguageConfig();
    when(service.create(expected)).thenReturn(expected);
    mockExecutor(consortiaTenantExecutor);

    var actual = decorator.create(expected);

    assertThat(actual).isEqualTo(expected);
    verify(service).create(expected);
    verify(consortiaTenantExecutor).execute(any());
  }

  @Test
  void update() {
    var code = "test";
    var expected = new LanguageConfig();
    when(service.update(code, expected)).thenReturn(expected);
    mockExecutor(consortiaTenantExecutor);

    var actual = decorator.update(code, expected);

    assertThat(actual).isEqualTo(expected);
    verify(service).update(code, expected);
    verify(consortiaTenantExecutor).execute(any());
  }

  @Test
  void delete() {
    var code = "test";
    mockExecutorRun(consortiaTenantExecutor);

    decorator.delete(code);

    verify(service).delete(code);
    verify(consortiaTenantExecutor).run(any());
  }

  @Test
  void getAll() {
    var expected = new LanguageConfigs();
    when(service.getAll()).thenReturn(expected);
    mockExecutor(consortiaTenantExecutor);

    var actual = decorator.getAll();

    assertThat(actual).isEqualTo(expected);
    verify(service).getAll();
    verify(consortiaTenantExecutor).execute(any());
  }

  @Test
  void getAllLanguageCodes() {
    var expected = Set.of("test");
    when(service.getAllLanguageCodes()).thenReturn(expected);
    mockExecutor(consortiaTenantExecutor);

    var actual = decorator.getAllLanguageCodes();

    assertThat(actual).isEqualTo(expected);
    verify(service).getAllLanguageCodes();
    verify(consortiaTenantExecutor).execute(any());
  }

  @Test
  void getAllLanguagesForTenant() {
    var expected = Set.of("test");
    when(service.getAllLanguagesForTenant(TENANT_ID)).thenReturn(expected);

    var actual = decorator.getAllLanguagesForTenant(TENANT_ID);

    assertThat(actual).isEqualTo(expected);
    verify(service).getAllLanguagesForTenant(TENANT_ID);
    verifyNoInteractions(consortiaTenantExecutor);
  }

}
