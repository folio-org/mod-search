package org.folio.search.integration.message.interceptor;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ElasticsearchRequestInterceptorTest {

  @InjectMocks
  private ElasticsearchRequestInterceptor interceptor;
  @Mock
  private HttpRequest request;
  @Mock
  private EntityDetails entityDetails;
  @Mock
  private HttpContext context;

  @Test
  void shouldModifyUriForSupportedMethodAndMatchingParam() {
    when(request.getMethod()).thenReturn("POST");
    when(request.getRequestUri()).thenReturn("/some/path?cluster_manager_timeout=30s");

    interceptor.process(request, entityDetails, context);

    verify(request).setPath("/some/path?master_timeout=30s");
  }

  @Test
  void shouldNotModifyUriForUnsupportedMethod() {
    when(request.getMethod()).thenReturn("HEAD");

    interceptor.process(request, entityDetails, context);

    verify(request, never()).setPath(anyString());
  }

  @Test
  void shouldNotModifyUriIfParamNotPresent() {
    when(request.getMethod()).thenReturn("POST");
    when(request.getRequestUri()).thenReturn("/some/path");

    interceptor.process(request, entityDetails, context);

    verify(request, never()).setPath(anyString());
  }
}
