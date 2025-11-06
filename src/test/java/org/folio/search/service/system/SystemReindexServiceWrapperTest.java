package org.folio.search.service.system;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE;

import java.util.concurrent.CompletableFuture;
import org.folio.search.model.metadata.ResourceDescription;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.IndexService;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.folio.search.service.reindex.ReindexService;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@UnitTest
@EnableRetry(proxyTargetClass = true)
@SpringBootTest(classes = SystemReindexServiceWrapper.class, webEnvironment = NONE)
class SystemReindexServiceWrapperTest {

  @MockitoBean
  private ReindexService reindexService;
  @MockitoBean
  private IndexService indexService;
  @MockitoBean
  private ResourceDescriptionService resourceDescriptionService;
  @Autowired
  private SystemReindexServiceWrapper systemReindexServiceWrapper;

  @Test
  void doReindex_shouldRetryOnFailure() {
    // Arrange
    ResourceType resource = mock(ResourceType.class);
    when(resource.getName()).thenReturn(ReindexEntityType.INSTANCE.getType());
    var resourceDescription = new ResourceDescription();
    resourceDescription.setReindexSupported(true);
    when(resourceDescriptionService.get(resource)).thenReturn(resourceDescription);
    String tenantId = "testTenant";
    when(reindexService.submitFullReindex(tenantId, null))
      .thenThrow(new RuntimeException("Test exception"))
      .thenReturn(CompletableFuture.completedFuture(null));

    // Act
    systemReindexServiceWrapper.doReindex(resource, tenantId);

    // Assert
    verify(reindexService, times(2)).submitFullReindex(tenantId, null);
  }

  @Test
  void doReindex_shouldHandleUnsupportedReindexGracefully() {
    // Arrange
    ResourceType resource = mock(ResourceType.class);
    var resourceDescription = new ResourceDescription();
    resourceDescription.setReindexSupported(false);
    when(resourceDescriptionService.get(resource)).thenReturn(resourceDescription);
    String tenantId = "testTenant";

    // Act
    systemReindexServiceWrapper.doReindex(resource, tenantId);

    // Assert
    verifyNoInteractions(reindexService, indexService);
  }
}
