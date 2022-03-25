package org.folio.search.service;

import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.languageConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import javax.persistence.EntityNotFoundException;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.domain.dto.LanguageConfig;
import org.folio.search.exception.ValidationException;
import org.folio.search.model.config.LanguageConfigEntity;
import org.folio.search.repository.LanguageConfigRepository;
import org.folio.search.service.metadata.LocalSearchFieldProvider;
import org.folio.search.utils.SearchUtils;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class LanguageConfigServiceTest {

  private static final String SUPPORTED_LANGUAGE_CODE = "supported";
  private static final String UNSUPPORTED_LANGUAGE_CODE = "unsupported";

  @InjectMocks private LanguageConfigService configService;
  @Mock private LanguageConfigRepository configRepository;
  @Mock private LocalSearchFieldProvider searchFieldProvider;
  @Mock private TenantScopedExecutionService tenantScopedExecutionService;
  @Mock private SearchConfigurationProperties searchConfigurationProperties;

  @Test
  void canAddLanguageConfig() {
    when(searchConfigurationProperties.getMaxSupportedLanguages()).thenReturn(5L);
    when(configRepository.count()).thenReturn(0L);
    when(searchFieldProvider.isSupportedLanguage(SUPPORTED_LANGUAGE_CODE)).thenReturn(true);
    when(configRepository.save(any(LanguageConfigEntity.class)))
      .thenReturn(new LanguageConfigEntity(SUPPORTED_LANGUAGE_CODE, null));

    final var saveResult = configService.create(new LanguageConfig().code(SUPPORTED_LANGUAGE_CODE));

    assertThat(saveResult.getCode(), is(SUPPORTED_LANGUAGE_CODE));
  }

  @Test
  void cannotAddConfigIfLanguageIsNotSupported() {
    final var languageConfig = new LanguageConfig().code(UNSUPPORTED_LANGUAGE_CODE);

    assertThrows(ValidationException.class, () -> configService.create(languageConfig));
  }

  @Test
  void cannotAddConfigIfLanguageIsSrc() {
    final var languageConfig = new LanguageConfig().code(SearchUtils.MULTILANG_SOURCE_SUBFIELD);

    assertThrows(ValidationException.class, () -> configService.create(languageConfig));
  }

  @Test
  void cannotAddConfigIfThereIsAlready5Languages() {
    final var languageConfig = new LanguageConfig().code(SUPPORTED_LANGUAGE_CODE);

    when(searchConfigurationProperties.getMaxSupportedLanguages()).thenReturn(5L);
    when(configRepository.count()).thenReturn(5L);
    when(searchFieldProvider.isSupportedLanguage(SUPPORTED_LANGUAGE_CODE)).thenReturn(true);

    assertThrows(ValidationException.class, () -> configService.create(languageConfig));
  }

  @Test
  void update_positive() {
    var analyzer = "custom-analyzer";
    var languageConfig = languageConfig(SUPPORTED_LANGUAGE_CODE, analyzer);
    var expectedEntity = new LanguageConfigEntity(SUPPORTED_LANGUAGE_CODE, analyzer);

    var entityById = new LanguageConfigEntity(SUPPORTED_LANGUAGE_CODE, null);
    when(configRepository.findById(SUPPORTED_LANGUAGE_CODE)).thenReturn(Optional.of(entityById));
    when(configRepository.save(expectedEntity)).thenReturn(expectedEntity);

    assertThat(configService.update(SUPPORTED_LANGUAGE_CODE, languageConfig), is(languageConfig));
  }

  @Test
  void update_positive_doNothingBecauseEntitiesAreTheSame() {
    var languageConfig = languageConfig(SUPPORTED_LANGUAGE_CODE, null);

    var entityById = new LanguageConfigEntity(SUPPORTED_LANGUAGE_CODE, null);
    when(configRepository.findById(SUPPORTED_LANGUAGE_CODE)).thenReturn(Optional.of(entityById));

    assertThat(configService.update(SUPPORTED_LANGUAGE_CODE, languageConfig), is(languageConfig));
    verify(configRepository, never()).save(any(LanguageConfigEntity.class));
  }

  @Test
  void update_negative_entityByIdIsNotFound() {
    var languageConfig = languageConfig(SUPPORTED_LANGUAGE_CODE);
    when(configRepository.findById(SUPPORTED_LANGUAGE_CODE)).thenReturn(Optional.empty());
    assertThrows(EntityNotFoundException.class, () -> configService.update(SUPPORTED_LANGUAGE_CODE, languageConfig));
  }

  @Test
  void update_negative_diffLanguageCodes() {
    var languageConfig = languageConfig(SUPPORTED_LANGUAGE_CODE);
    assertThrows(ValidationException.class, () -> configService.update(UNSUPPORTED_LANGUAGE_CODE, languageConfig));
  }

  @Test
  void getAllLanguagesForTenant_positive() {
    var entities = List.of(new LanguageConfigEntity("eng", null), new LanguageConfigEntity("kor", "nori"));
    when(configRepository.findAll()).thenReturn(entities);
    when(tenantScopedExecutionService.executeTenantScoped(eq(TENANT_ID), any()))
      .then(inv -> inv.<Callable<Set<String>>>getArgument(1).call());
    var actual = configService.getAllLanguagesForTenant(TENANT_ID);
    assertThat(actual, is(Set.of("eng", "kor")));
  }
}
