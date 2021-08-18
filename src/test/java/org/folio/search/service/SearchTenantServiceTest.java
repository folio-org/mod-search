package org.folio.search.service;

import static org.folio.search.model.SearchResource.INSTANCE;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.domain.dto.LanguageConfig;
import org.folio.search.service.systemuser.SystemUserService;
import org.folio.search.utils.types.UnitTest;
import org.folio.spring.FolioExecutionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class SearchTenantServiceTest {

  @InjectMocks private SearchTenantService searchTenantService;
  @Mock private IndexService indexService;
  @Mock private FolioExecutionContext context;
  @Mock private SystemUserService systemUserService;
  @Mock private LanguageConfigService languageConfigService;
  @Mock private SearchConfigurationProperties searchConfigurationProperties;

  @Test
  void initializeTenant_positive() {
    when(searchConfigurationProperties.getInitialLanguages()).thenReturn(Set.of("eng"));
    when(context.getTenantId()).thenReturn(TENANT_ID);
    doNothing().when(systemUserService).prepareSystemUser();

    searchTenantService.initializeTenant();

    verify(languageConfigService).create(new LanguageConfig().code("eng"));
    verify(indexService).createIndexIfNotExist(INSTANCE.getName(), TENANT_ID);
  }

  @Test
  void initializeTenant_shouldNotCreateLanguageIfAlreadyExist() {
    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(searchConfigurationProperties.getInitialLanguages()).thenReturn(Set.of("eng", "fre"));
    when(languageConfigService.getAllLanguageCodes()).thenReturn(Set.of("eng"));
    doNothing().when(systemUserService).prepareSystemUser();

    searchTenantService.initializeTenant();

    verify(languageConfigService, times(0)).create(new LanguageConfig().code("eng"));
    verify(languageConfigService).create(new LanguageConfig().code("fre"));
    verify(indexService).createIndexIfNotExist(INSTANCE.getName(), TENANT_ID);
  }
}
