package org.folio.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.model.service.CqlResourceIdsRequest;
import org.folio.search.service.consortium.ResourceIdServiceDecorator;
import org.folio.spring.test.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ResourceIdsStreamHelperTest {

  @InjectMocks
  private ResourceIdsStreamHelper resourceIdsStreamHelper;
  @Mock
  private ResourceIdServiceDecorator resourceIdService;

  @Test
  void streamResourceIds_positive() throws IOException {
    var servletRequestAttributes = mock(ServletRequestAttributes.class);
    RequestContextHolder.setRequestAttributes(servletRequestAttributes);
    var httpServletResponse = mock(HttpServletResponse.class);
    var outputStream = mock(ServletOutputStream.class);
    when(servletRequestAttributes.getResponse()).thenReturn(httpServletResponse);
    when(httpServletResponse.getOutputStream()).thenReturn(outputStream);

    var request = CqlResourceIdsRequest.of("id=*", RESOURCE_NAME, TENANT_ID, "id");
    doNothing().when(resourceIdService).streamResourceIdsAsJson(request, outputStream);

    var actual = resourceIdsStreamHelper.streamResourceIds(request, APPLICATION_JSON_VALUE);
    assertThat(actual).isEqualTo(ResponseEntity.ok().build());
  }

  @Test
  void streamResourceIds_positive_NullContentType() throws IOException {
    var servletRequestAttributes = mock(ServletRequestAttributes.class);
    RequestContextHolder.setRequestAttributes(servletRequestAttributes);
    var httpServletResponse = mock(HttpServletResponse.class);
    var outputStream = mock(ServletOutputStream.class);
    when(servletRequestAttributes.getResponse()).thenReturn(httpServletResponse);
    when(httpServletResponse.getOutputStream()).thenReturn(outputStream);

    var request = CqlResourceIdsRequest.of("id=*", RESOURCE_NAME, TENANT_ID, "id");
    doNothing().when(resourceIdService).streamResourceIdsAsJson(request, outputStream);

    var actual = resourceIdsStreamHelper.streamResourceIds(request, null);
    assertThat(actual).isEqualTo(ResponseEntity.ok().build());
  }

  @Test
  void streamResourceIdsTextType_positive() throws IOException {
    var servletRequestAttributes = mock(ServletRequestAttributes.class);
    RequestContextHolder.setRequestAttributes(servletRequestAttributes);
    var httpServletResponse = mock(HttpServletResponse.class);
    var outputStream = mock(ServletOutputStream.class);
    when(servletRequestAttributes.getResponse()).thenReturn(httpServletResponse);
    when(httpServletResponse.getOutputStream()).thenReturn(outputStream);

    var request = CqlResourceIdsRequest.of("id=*", RESOURCE_NAME, TENANT_ID, "id");
    doNothing().when(resourceIdService).streamResourceIdsAsText(request, outputStream);

    var actual = resourceIdsStreamHelper.streamResourceIds(request, TEXT_PLAIN_VALUE);
    assertThat(actual).isEqualTo(ResponseEntity.ok().build());
  }

  @Test
  void streamResourceIds_negative_nullRequestAttributes() {
    RequestContextHolder.setRequestAttributes(null);

    var request = CqlResourceIdsRequest.of("id=*", RESOURCE_NAME, TENANT_ID, "id");
    assertThatThrownBy(() -> resourceIdsStreamHelper.streamResourceIds(request, APPLICATION_JSON_VALUE))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Request attributes must be not null");
  }

  @Test
  void streamResourceIds_negative_nullHttpServletResponse() {
    var servletRequestAttributes = mock(ServletRequestAttributes.class);
    RequestContextHolder.setRequestAttributes(servletRequestAttributes);
    when(servletRequestAttributes.getResponse()).thenReturn(null);

    var request = CqlResourceIdsRequest.of("id=*", RESOURCE_NAME, TENANT_ID, "id");
    assertThatThrownBy(() -> resourceIdsStreamHelper.streamResourceIds(request, APPLICATION_JSON_VALUE))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("HttpServletResponse must be not null");
  }

  @Test
  void streamResourceIds_negative_errorOnReceivingOutputStream() throws IOException {
    var servletRequestAttributes = mock(ServletRequestAttributes.class);
    RequestContextHolder.setRequestAttributes(servletRequestAttributes);
    var httpServletResponse = mock(HttpServletResponse.class);
    when(servletRequestAttributes.getResponse()).thenReturn(httpServletResponse);
    when(httpServletResponse.getOutputStream()).thenThrow(new IOException("error"));

    var request = CqlResourceIdsRequest.of("id=*", RESOURCE_NAME, TENANT_ID, "id");
    assertThatThrownBy(() -> resourceIdsStreamHelper.streamResourceIds(request, APPLICATION_JSON_VALUE))
      .isInstanceOf(SearchServiceException.class)
      .hasMessage("Failed to get output stream from response");
  }
}
