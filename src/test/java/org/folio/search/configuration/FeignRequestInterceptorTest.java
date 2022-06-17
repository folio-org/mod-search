package org.folio.search.configuration;

import static java.util.Collections.singletonList;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;
import static org.folio.spring.integration.XOkapiHeaders.TOKEN;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import feign.RequestTemplate;
import org.folio.search.utils.types.UnitTest;
import org.folio.spring.FolioExecutionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class FeignRequestInterceptorTest {

  @InjectMocks
  private FeignRequestInterceptor requestInterceptor;
  @Mock
  private FolioExecutionContext folioExecutionContext;
  @Mock
  private RequestTemplate requestTemplate;

  @Test
  void apply_positive() {
    when(folioExecutionContext.getToken()).thenReturn("token");
    when(folioExecutionContext.getTenantId()).thenReturn("tenantId");
    requestInterceptor.apply(requestTemplate);

    verify(requestTemplate).header(TOKEN, singletonList("token"));
    verify(requestTemplate).header(TENANT, singletonList("tenantId"));
  }
}
