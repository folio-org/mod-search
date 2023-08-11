package org.folio.search.service;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.model.service.CqlResourceIdsRequest;
import org.folio.search.service.consortium.ConsortiumTenantExecutor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Log4j2
@Component
@RequiredArgsConstructor
public class ResourceIdsStreamHelper {

  private final ResourceIdService resourceIdService;
  private final ConsortiumTenantExecutor consortiumTenantExecutor;

  /**
   * Provides ability to stream resource ids using given request object.
   *
   * @param request     - request as {@link CqlResourceIdsRequest} object
   * @param contentType - Content-Type header value
   * @return response with found resource ids using http streaming approach.
   */
  public ResponseEntity<Void> streamResourceIds(CqlResourceIdsRequest request, String contentType) {
    log.debug("streamResourceIds:: by [request: {}, contentType: {}]", request, contentType);

    try {
      var httpServletResponse = prepareHttpResponse();
      if (contentType != null && contentType.contains(TEXT_PLAIN_VALUE)) {
        httpServletResponse.setContentType(TEXT_PLAIN_VALUE);
        resourceIdService.streamResourceIdsAsText(request, httpServletResponse.getOutputStream());
      } else {
        httpServletResponse.setContentType(APPLICATION_JSON_VALUE);
        resourceIdService.streamResourceIdsAsJson(request, httpServletResponse.getOutputStream());
      }
      return ResponseEntity.ok().build();
    } catch (IOException e) {
      throw new SearchServiceException("Failed to get output stream from response", e);
    }
  }

  /**
   * Provides ability to stream prepared resource ids from the database using given request object.
   *
   * @param jobId - async jobs id with prepared query
   * @return response with found resource ids using http streaming approach.
   */
  public ResponseEntity<Void> streamResourceIdsFromDb(String jobId) {
    log.debug("streamResourceIdsFromDb:: by [jobId: {}]", jobId);

    try {
      var httpServletResponse = prepareHttpResponse();
      httpServletResponse.setContentType(APPLICATION_JSON_VALUE);

      var outputStream = httpServletResponse.getOutputStream();
      consortiumTenantExecutor.run(() ->
      resourceIdService.streamIdsFromDatabaseAsJson(jobId, outputStream));
      return ResponseEntity.ok().build();
    } catch (IOException e) {
      throw new SearchServiceException("Failed to get output stream from response", e);
    }
  }

  private HttpServletResponse prepareHttpResponse() {
    var requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    Assert.notNull(requestAttributes, "Request attributes must be not null");

    var httpServletResponse = requestAttributes.getResponse();
    Assert.notNull(httpServletResponse, "HttpServletResponse must be not null");

    httpServletResponse.setStatus(HttpServletResponse.SC_OK);
    return httpServletResponse;
  }
}
