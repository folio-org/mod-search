package org.folio.search.service;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.spring.scope.FolioExecutionScopeExecutionContextManager.beginFolioExecutionContext;
import static org.folio.spring.scope.FolioExecutionScopeExecutionContextManager.endFolioExecutionContext;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import org.folio.search.domain.dto.LanguageConfig;
import org.folio.search.repository.LanguageConfigRepository;
import org.folio.search.utils.types.IntegrationTest;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.liquibase.FolioSpringLiquibase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;

@IntegrationTest
@SpringBootTest
class LanguageConfigCachingIT {
  private static final String TENANT_ONE = "tenant_one";
  private static final String TENANT_TWO = "tenant_two";

  @Autowired
  private CacheManager cacheManager;
  @Autowired
  private LanguageConfigService configService;
  @Autowired
  private FolioModuleMetadata moduleMetadata;

  @BeforeAll
  static void setUpTenants(@Autowired FolioModuleMetadata moduleMetadata,
    @Autowired FolioSpringLiquibase folioSpringLiquibase) throws Exception {

    for (String tenant : List.of(TENANT_ONE, TENANT_TWO)) {
      beginFolioExecutionContext(new AsyncFolioExecutionContext(tenant, moduleMetadata));

      folioSpringLiquibase.setDefaultSchema(moduleMetadata.getDBSchemaName(tenant));
      folioSpringLiquibase.performLiquibaseUpdate();
    }
  }

  @BeforeEach
  void removeConfigs(@Autowired LanguageConfigRepository repository) {
    runInScope(TENANT_ONE, notUsed -> repository.deleteAll());
    runInScope(TENANT_TWO, notUsed -> repository.deleteAll());
  }

  @Test
  void shouldCacheGetAllLanguagesForTenant() {
    final List<String> languagesForTenantOne = List.of("eng", "rus");
    final List<String> languagesForTenantTwo = List.of("ara", "fre");

    createLanguagesAndInitiateCaching(TENANT_ONE, languagesForTenantOne);
    createLanguagesAndInitiateCaching(TENANT_TWO, languagesForTenantTwo);

    assertThat(getCachedValue(TENANT_ONE))
      .containsExactlyInAnyOrderElementsOf(languagesForTenantOne);
    assertThat(getCachedValue(TENANT_TWO))
      .containsExactlyInAnyOrderElementsOf(languagesForTenantTwo);
  }

  @Test
  void shouldEvictCacheOnLanguageDelete() {
    final List<String> languagesForTenantOne = List.of("eng", "rus");
    final List<String> languagesForTenantTwo = List.of("ara", "fre");

    createLanguagesAndInitiateCaching(TENANT_ONE, languagesForTenantOne);
    createLanguagesAndInitiateCaching(TENANT_TWO, languagesForTenantTwo);

    runInScope(TENANT_ONE, tenant -> configService.delete("eng"));

    assertThat(getCachedValue(TENANT_ONE)).isNullOrEmpty();
    assertThat(getCachedValue(TENANT_TWO))
      .containsExactlyInAnyOrderElementsOf(languagesForTenantTwo);

    runInScope(TENANT_ONE, tenant -> assertThat(configService
      .getAllLanguagesForTenant(tenant)).containsExactly("rus"));
  }

  @Test
  void shouldEvictCacheWhenLanguageAdded() {
    final List<String> languagesForTenantOne = List.of("eng", "rus");
    final List<String> languagesForTenantTwo = List.of("ara", "fre");

    createLanguagesAndInitiateCaching(TENANT_ONE, languagesForTenantOne);
    createLanguagesAndInitiateCaching(TENANT_TWO, languagesForTenantTwo);

    runInScope(TENANT_TWO, tenant -> configService.create(
      new LanguageConfig().code("heb")));

    assertThat(getCachedValue(TENANT_ONE))
      .containsExactlyInAnyOrderElementsOf(languagesForTenantOne);
    assertThat(getCachedValue(TENANT_TWO)).isNullOrEmpty();

    runInScope(TENANT_TWO, tenant -> assertThat(configService
      .getAllLanguagesForTenant(tenant))
      .containsExactlyInAnyOrder("ara", "fre", "heb"));
  }

  @SuppressWarnings("unchecked")
  private Collection<String> getCachedValue(String tenant) {
    final var cache = cacheManager.getCache("language-config");
    return cache != null ? cache.get(tenant, Collection.class) : emptyList();
  }

  private void createLanguagesAndInitiateCaching(String tenant, List<String> codes) {
    runInScope(tenant, notUsed -> {
      codes.forEach(lang -> configService.create(new LanguageConfig().code(lang)));

      assertThat(configService.getAllLanguagesForTenant(tenant))
        .containsExactlyInAnyOrderElementsOf(codes);
    });
  }

  private void runInScope(String tenant, Consumer<String> action) {
    beginFolioExecutionContext(new AsyncFolioExecutionContext(tenant, moduleMetadata));
    action.accept(tenant);
    endFolioExecutionContext();
  }
}
