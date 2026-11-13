package org.folio.search.service.indexrebuild;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.folio.search.model.types.ResourceType;
import org.folio.search.service.es.SearchMappingsHelper;
import org.folio.search.service.es.SearchSettingsHelper;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@UnitTest
@ExtendWith(MockitoExtension.class)
class IndexFingerprintServiceTest {

  private static final String SETTINGS = "{\"index\":{\"number_of_replicas\":%d},\"analysis\":{\"filter\":\"%s\"}}";

  @Mock
  private SearchMappingsHelper mappingsHelper;
  @Mock
  private SearchSettingsHelper settingsHelper;
  @InjectMocks
  private IndexFingerprintService service;

  @Test
  void current_positive_sameInputsGiveSameResult() {
    assertThat(fingerprint("m", 1, "a")).isEqualTo(fingerprint("m", 1, "a"));
  }

  @Test
  void current_positive_replicasDoNotAffectResult() {
    assertThat(fingerprint("m", 1, "a")).isEqualTo(fingerprint("m", 2, "a"));
  }

  @Test
  void current_positive_mappingsAffectResult() {
    assertThat(fingerprint("m", 1, "a")).isNotEqualTo(fingerprint("other", 1, "a"));
  }

  @Test
  void current_positive_analysisAffectsResult() {
    assertThat(fingerprint("m", 1, "a")).isNotEqualTo(fingerprint("m", 1, "other"));
  }

  private String fingerprint(String mappings, int replicas, String analysis) {
    when(mappingsHelper.getMappings(ResourceType.ITEM)).thenReturn(mappings);
    when(settingsHelper.getSettingsJson(ResourceType.ITEM))
      .thenReturn(new JsonMapper().readTree(SETTINGS.formatted(replicas, analysis)));
    return service.current(IndexRebuildTarget.ITEM);
  }
}
