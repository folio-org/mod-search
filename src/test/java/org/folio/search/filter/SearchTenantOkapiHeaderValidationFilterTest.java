package org.folio.search.filter;

import static org.folio.search.filter.SearchTenantOkapiHeaderValidationFilter.ValidationProperties;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.util.Set;
import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.folio.search.utils.types.UnitTest;
import org.folio.spring.integration.XOkapiHeaders;
import org.junit.jupiter.api.Test;

@UnitTest
class SearchTenantOkapiHeaderValidationFilterTest {
  @Test
  void shouldAllowRequestIfExcluded() throws Exception {
    var path = "/excluded/path";
    var request = mock(HttpServletRequest.class);
    var chain = mock(FilterChain.class);

    when(request.getRequestURI()).thenReturn(path);

    doFilter(request, chain, path, "/another/path");

    verify(chain).doFilter(eq(request), any());
    verify(request, times(0)).getHeader(XOkapiHeaders.TENANT);
  }

  @Test
  void shouldAllowRequestIfNotExcluded() throws Exception {
    var request = mock(HttpServletRequest.class);
    var chain = mock(FilterChain.class);

    when(request.getRequestURI()).thenReturn("/not/excluded");
    when(request.getHeader(XOkapiHeaders.TENANT)).thenReturn("tenant");

    doFilter(request, chain, "/excluded/path");

    verify(request).getHeader(XOkapiHeaders.TENANT);
    verify(chain).doFilter(eq(request), any());
  }

  @Test
  void shouldDisallowRequestIfNotExcludedAndNoTenantHeader() throws Exception {
    var request = mock(HttpServletRequest.class);
    var chain = mock(FilterChain.class);

    when(request.getRequestURI()).thenReturn("/not/excluded");

    doFilter(request, chain, "/excluded/path");

    verify(request).getHeader(XOkapiHeaders.TENANT);
    verify(chain, times(0)).doFilter(eq(request), any());
  }

  private void doFilter(HttpServletRequest rq, FilterChain filterChain, String... excludePaths) throws Exception {
    var response = mock(HttpServletResponse.class);
    when(response.getWriter()).thenReturn(mock(PrintWriter.class));

    new SearchTenantOkapiHeaderValidationFilter(ValidationProperties.of(Set.of(excludePaths)))
      .doFilter(rq, response, filterChain);
  }
}
