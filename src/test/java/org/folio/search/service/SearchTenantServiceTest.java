package org.folio.search.service;

import static org.folio.search.model.SearchResource.INSTANCE;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.folio.search.domain.dto.LanguageConfig;
import org.folio.search.utils.types.UnitTest;
import org.folio.spring.FolioExecutionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class SearchTenantServiceTest {
  private static final String TENANT_NAME = "tenant";
  @Mock
  private IndexService indexService;
  @Mock
  private FolioExecutionContext context;
  @Mock
  private LanguageConfigService languageConfigService;

  @Test
  void initializeTenant_positive() {
    var service = new SearchTenantService(indexService, context, Set.of("eng"), languageConfigService);
    when(context.getTenantId()).thenReturn(TENANT_NAME);

    service.initializeTenant();

    verify(languageConfigService).create(new LanguageConfig().code("eng"));
    verify(indexService).createIndexIfNotExist(INSTANCE.getName(), TENANT_NAME);
  }

  @Test
  void initializeTenant_shouldNotCreateLanguageIfAlreadyExist() {
    var service = new SearchTenantService(indexService, context, Set.of("eng", "fre"), languageConfigService);
    when(context.getTenantId()).thenReturn(TENANT_NAME);
    when(languageConfigService.getAllLanguageCodes()).thenReturn(Set.of("eng"));

    service.initializeTenant();

    verify(languageConfigService, times(0)).create(new LanguageConfig().code("eng"));
    verify(languageConfigService).create(new LanguageConfig().code("fre"));
    verify(indexService).createIndexIfNotExist(INSTANCE.getName(), TENANT_NAME);
  }
}
