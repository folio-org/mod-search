package org.folio.search.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.model.types.ClassificationType;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ClassificationTypeHelperTest {

  private @Mock ReferenceDataService referenceDataService;
  private @Mock SearchConfigurationProperties configurationProperties;
  private @InjectMocks ClassificationTypeHelper typeHelper;

  @Test
  void getClassificationTypeMap_ReturnsEmptyMap_WhenBrowseClassificationTypesIsEmpty() {
    // Arrange
    when(configurationProperties.getBrowseClassificationTypes()).thenReturn(Collections.emptyMap());

    // Act
    Map<String, ClassificationType> result = typeHelper.getClassificationTypeMap();

    // Assert
    assertEquals(Collections.emptyMap(), result);
  }

  @Test
  void getClassificationTypeMap_ReturnsCorrectMap_WhenBrowseClassificationTypesIsNotEmpty() {
    // Arrange
    Map<ClassificationType, String[]> browseClassificationTypes = new HashMap<>();
    browseClassificationTypes.put(ClassificationType.NLM, new String[] {"nlm_type"});
    browseClassificationTypes.put(ClassificationType.LC, new String[] {"lc_type", "lc_additional"});

    when(configurationProperties.getBrowseClassificationTypes()).thenReturn(browseClassificationTypes);
    when(referenceDataService.fetchReferenceData(any(), any(), eq(List.of("nlm_type"))))
      .thenReturn(Collections.singleton("nlmId"));
    when(referenceDataService.fetchReferenceData(any(), any(), eq(List.of("lc_type", "lc_additional"))))
      .thenReturn(Set.of("lcId1", "lcId2"));

    // Act
    Map<String, ClassificationType> result = typeHelper.getClassificationTypeMap();

    // Assert
    assertEquals(3, result.size());
    assertEquals(ClassificationType.NLM, result.get("nlmId"));
    assertEquals(ClassificationType.LC, result.get("lcId1"));
    assertEquals(ClassificationType.LC, result.get("lcId2"));
  }

}
