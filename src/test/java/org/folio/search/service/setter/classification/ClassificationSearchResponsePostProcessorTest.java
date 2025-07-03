package org.folio.search.service.setter.classification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.folio.search.model.index.ClassificationResource;
import org.folio.search.model.index.InstanceSubResource;
import org.folio.search.repository.SearchRepository;
import org.folio.search.service.consortium.TenantProvider;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ClassificationSearchResponsePostProcessorTest {

  @Mock
  private SearchRepository searchRepository;
  @Mock
  private FolioExecutionContext context;
  @Mock
  private TenantProvider tenantProvider;
  @Mock
  private SearchResponse searchResponse;
  @Mock
  private SearchHits searchHits;
  @Mock
  private SearchHit searchHit;

  @InjectMocks
  private ClassificationSearchResponsePostProcessor postProcessor;

  @BeforeEach
  void setUp() {
    lenient().when(searchRepository.search(any(), any())).thenReturn(searchResponse);
    lenient().when(searchResponse.getHits()).thenReturn(searchHits);
    lenient().when(searchHits.getHits()).thenReturn(new SearchHit[]{searchHit});
  }

  @Test
  void getGeneric_shouldReturnClassificationResourceClass() {
    // Act
    var result = postProcessor.getGeneric();

    // Assert
    assertThat(result).isEqualTo(ClassificationResource.class);
  }

  @Test
  void process_shouldDoNothingWhenResourcesAreNull() {
    // Act
    postProcessor.process(null);

    // Assert
    verifyNoInteractions(searchRepository, context, tenantProvider);
  }

  @Test
  void process_shouldDoNothingWhenResourcesAreEmpty() {
    // Act
    postProcessor.process(List.of());

    // Assert
    verifyNoInteractions(searchRepository, context, tenantProvider);
  }

  @Test
  void process_shouldProcessResources() {
    // Arrange
    var source1 = Map.of(
      "tenantId", "tenant1",
      "classificationIds", List.of("classification1"),
      "plain_title", "Title 1",
      "contributors", List.of(Map.of("name", "Contributor 1"))
    );

    var source2 = Map.of(
      "tenantId", "tenant2",
      "classificationIds", List.of("classification1"),
      "plain_title", "Title 2",
      "contributors", List.of(Map.of("name", "Contributor 2"))
    );

    var searchHit1 = mock(SearchHit.class);
    var searchHit2 = mock(SearchHit.class);
    when(searchHit1.getSourceAsMap()).thenReturn(source1);
    when(searchHit2.getSourceAsMap()).thenReturn(source2);

    when(searchHits.getHits()).thenReturn(new SearchHit[]{searchHit1, searchHit2});
    when(context.getTenantId()).thenReturn("tenant1");

    var instanceSubResource1 = InstanceSubResource.builder()
      .tenantId("tenant1")
      .count(1)
      .build();
    var instanceSubResource2 = InstanceSubResource.builder()
      .tenantId("tenant2")
      .count(1)
      .build();

    var classificationResource = new ClassificationResource("classification1", null, "cl1",
      Set.of(instanceSubResource1, instanceSubResource2));

    // Act
    postProcessor.process(List.of(classificationResource));

    // Assert
    assertThat(instanceSubResource1.getInstanceTitle()).isEqualTo("Title 1");
    assertThat(instanceSubResource1.getInstanceContributors()).containsExactly("Contributor 1");
    assertThat(instanceSubResource2.getInstanceTitle()).isEqualTo("Title 2");
    assertThat(instanceSubResource2.getInstanceContributors()).containsExactly("Contributor 2");
  }

  @Test
  void process_shouldHandleInvalidTenantId() {
    // Arrange
    var instanceSubResource = InstanceSubResource.builder()
      .tenantId("invalidTenant")
      .count(1)
      .build();

    var classificationResource = new ClassificationResource("classification1", null, "cl1",
      Set.of(instanceSubResource));

    var source = Map.of(
      "tenantId", "tenant1",
      "classificationIds", List.of("classification1"),
      "plain_title", "Test Title",
      "contributors", List.of(Map.of("name", "Contributor Name"))
    );

    when(searchHit.getSourceAsMap()).thenReturn(source);
    when(context.getTenantId()).thenReturn("tenant1");

    // Act
    postProcessor.process(List.of(classificationResource));

    // Assert
    assertThat(instanceSubResource.getInstanceTitle()).isNull();
    assertThat(instanceSubResource.getInstanceContributors()).isNull();
  }

  @Test
  void process_shouldHandleMissingClassificationIds() {
    // Arrange
    var instanceSubResource = InstanceSubResource.builder()
      .tenantId("tenant1")
      .count(1)
      .build();

    var classificationResource = new ClassificationResource("classification1", null, "cl1",
      Set.of(instanceSubResource));

    var source = Map.of(
      "tenantId", "tenant1",
      "plain_title", "Test Title",
      "contributors", List.of(Map.of("name", "Contributor Name"))
    );

    when(searchHit.getSourceAsMap()).thenReturn(source);
    when(context.getTenantId()).thenReturn("tenant1");

    // Act
    postProcessor.process(List.of(classificationResource));

    // Assert
    assertThat(instanceSubResource.getInstanceTitle()).isNull();
    assertThat(instanceSubResource.getInstanceContributors()).isNull();
  }

  @Test
  void process_shouldHandleSubResourceWithoutTenantId() {
    // Arrange
    var instanceSubResource = InstanceSubResource.builder()
      .count(1)
      .build();

    var classificationResource = new ClassificationResource("classification1", null, "cl1",
      Set.of(instanceSubResource));

    var source = Map.of(
      "classificationIds", List.of("classification1"),
      "plain_title", "Test Title",
      "contributors", List.of(Map.of("name", "Contributor Name"))
    );

    when(searchHit.getSourceAsMap()).thenReturn(source);

    // Act
    postProcessor.process(List.of(classificationResource));

    // Assert
    assertThat(instanceSubResource.getInstanceTitle()).isEqualTo("Test Title");
    assertThat(instanceSubResource.getInstanceContributors()).containsExactly("Contributor Name");

    // Verify the query sent to the search repository
    verify(searchRepository).search(any(), argThat(query -> {
      var queryString = query.toString();
      assertThat(queryString).contains("{\"match\":{\"classificationIds");
      assertThat(queryString).doesNotContain("{\"match\":{\"tenantId");
      System.out.println("Query sent to search repository: " + queryString);
      return true;
    }));
  }
}
