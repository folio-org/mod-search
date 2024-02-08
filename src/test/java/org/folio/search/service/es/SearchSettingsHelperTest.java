package org.folio.search.service.es;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.utils.JsonUtils.jsonObject;
import static org.folio.search.utils.TestConstants.EMPTY_OBJECT;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.mockito.Mockito.when;

import org.apache.commons.lang3.SerializationException;
import org.folio.search.exception.ResourceDescriptionException;
import org.folio.search.service.metadata.LocalFileProvider;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class SearchSettingsHelperTest {

  @InjectMocks
  private SearchSettingsHelper settingsHelper;
  @Mock
  private JsonConverter jsonConverter;
  @Mock
  private LocalFileProvider localFileProvider;

  @Test
  void getSettings_positive() {
    when(localFileProvider.read("elasticsearch/index/test-resource.json")).thenReturn(EMPTY_OBJECT);
    when(jsonConverter.asJsonTree(EMPTY_OBJECT)).thenReturn(jsonObject());
    var settings = settingsHelper.getSettings(RESOURCE_NAME);
    assertThat(settings).isEqualTo(EMPTY_OBJECT);
  }

  @Test
  void getSettings_negative() {
    when(localFileProvider.read("elasticsearch/index/test-resource.json")).thenReturn(EMPTY_OBJECT);
    when(jsonConverter.asJsonTree(EMPTY_OBJECT)).thenThrow(new SerializationException("error"));
    assertThatThrownBy(() -> settingsHelper.getSettings(RESOURCE_NAME))
      .isInstanceOf(ResourceDescriptionException.class)
      .hasMessageContaining("Failed to load resource index settings [resourceName: test-resource]");
  }
}
