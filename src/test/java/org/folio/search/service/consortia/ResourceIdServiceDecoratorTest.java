package org.folio.search.service.consortia;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.OutputStream;
import org.folio.search.model.service.CqlResourceIdsRequest;
import org.folio.search.model.streamids.ResourceIdsJobEntity;
import org.folio.search.service.ResourceIdService;
import org.folio.search.service.consortium.ConsortiumTenantExecutor;
import org.folio.search.service.consortium.ResourceIdServiceDecorator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResourceIdServiceDecoratorTest extends DecoratorBaseTest {

  @Mock
  private ConsortiumTenantExecutor consortiumTenantExecutor;
  @Mock
  private ResourceIdService service;
  @InjectMocks
  private ResourceIdServiceDecorator decorator;

  @Test
  void streamResourceIdsAsText() {
    var request = mockRequest();
    var stream = mockOutputStream();
    decorator.streamResourceIdsAsText(request, stream);

    verify(service).streamResourceIdsAsText(request, stream);
    verifyNoInteractions(consortiumTenantExecutor);
  }

  @Test
  void streamIdsFromDatabaseAsJson() {
    var jobId = "test";
    var stream = mockOutputStream();
    mockExecutorRun(consortiumTenantExecutor);

    decorator.streamIdsFromDatabaseAsJson(jobId, stream);

    verify(service).streamIdsFromDatabaseAsJson(jobId, stream);
    verify(consortiumTenantExecutor).run(any());
  }

  @Test
  void streamResourceIdsForJob() {
    var job = new ResourceIdsJobEntity();
    var tenantId = "test";
    mockExecutorRun(consortiumTenantExecutor);

    decorator.streamResourceIdsForJob(job, tenantId);

    verify(service).streamResourceIdsForJob(job, tenantId);
    verify(consortiumTenantExecutor).run(any());
  }

  @Test
  void streamResourceIdsAsJson() {
    var request = mockRequest();
    var stream = mockOutputStream();
    decorator.streamResourceIdsAsJson(request, stream);

    verify(service).streamResourceIdsAsJson(request, stream);
    verifyNoInteractions(consortiumTenantExecutor);
  }

  private CqlResourceIdsRequest mockRequest() {
    return Mockito.mock(CqlResourceIdsRequest.class);
  }

  private OutputStream mockOutputStream() {
    return Mockito.mock(OutputStream.class);
  }

}
