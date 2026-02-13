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

  private static final String TENANT_1 = "tenant1";
  private static final String TENANT_2 = "tenant2";

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
    lenient().when(searchHits.getHits()).thenReturn(new SearchHit[] {searchHit});
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
    var title1 = "Title 1";
    var title2 = "Title 2";
    var contributor1 = "Contributor 1";
    var contributor2 = "Contributor 2";

    var searchHit1 = mock(SearchHit.class);
    var searchHit2 = mock(SearchHit.class);
    when(searchHit1.getSourceAsMap()).thenReturn(prepareSource(TENANT_1, title1, contributor1));
    when(searchHit2.getSourceAsMap()).thenReturn(prepareSource(TENANT_2, title2, contributor2));

    when(searchHits.getHits()).thenReturn(new SearchHit[] {searchHit1, searchHit2});
    when(context.getTenantId()).thenReturn(TENANT_1);

    var instanceSubResource1 = InstanceSubResource.builder().tenantId(TENANT_1).count(1).build();
    var instanceSubResource2 = InstanceSubResource.builder().tenantId(TENANT_2).count(1).build();

    var classificationResource = new ClassificationResource("classification1", null, "cl1",
      Set.of(instanceSubResource1, instanceSubResource2));

    // Act
    postProcessor.process(List.of(classificationResource));

    // Assert
    assertThat(instanceSubResource1.getInstanceTitle()).isEqualTo(title1);
    assertThat(instanceSubResource1.getInstanceContributors()).containsExactly(contributor1);
    assertThat(instanceSubResource2.getInstanceTitle()).isEqualTo(title2);
    assertThat(instanceSubResource2.getInstanceContributors()).containsExactly(contributor2);
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

    when(searchHit.getSourceAsMap()).thenReturn(prepareSource(TENANT_1, "Test Title", "Contributor Name"));
    when(context.getTenantId()).thenReturn(TENANT_1);

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
      .tenantId(TENANT_1)
      .count(1)
      .build();

    var classificationResource = new ClassificationResource("classification1", null, "cl1",
      Set.of(instanceSubResource));

    var source = Map.of(
      "tenantId", TENANT_1,
      "plain_title", "Test Title",
      "contributors", List.of(Map.of("name", "Contributor Name"))
    );

    when(searchHit.getSourceAsMap()).thenReturn(source);
    when(context.getTenantId()).thenReturn(TENANT_1);

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
      "classificationId", List.of("classification1"),
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
      assertThat(queryString).contains("{\"match\":{\"classificationId");
      assertThat(queryString).doesNotContain("{\"match\":{\"tenantId");
      System.out.println("Query sent to search repository: " + queryString);
      return true;
    }));
  }

  private Map<String, Object> prepareSource(String tenant, String title, String contributor) {
    return Map.of(
      "tenantId", tenant,
      "classificationId", List.of("classification1"),
      "plain_title", title,
      "contributors", List.of(Map.of("name", contributor))
    );
  }
}
