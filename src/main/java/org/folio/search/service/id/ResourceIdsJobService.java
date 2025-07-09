package org.folio.search.service.id;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Date;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.RandomStringUtils;
import org.folio.search.converter.ResourceIdsJobMapper;
import org.folio.search.domain.dto.ResourceIdsJob;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.model.streamids.ResourceIdsJobEntity;
import org.folio.search.model.types.StreamJobStatus;
import org.folio.search.repository.ResourceIdsJobRepository;
import org.folio.search.service.consortium.ConsortiumTenantExecutor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Log4j2
@Service
@RequiredArgsConstructor
public class ResourceIdsJobService {

  private final ConsortiumTenantExecutor consortiumTenantExecutor;
  private final ResourceIdsJobRepository jobRepository;
  private final ResourceIdsJobMapper resourceIdsJobMapper;
  private final ResourceIdService resourceIdService;
  private final Executor streamIdsExecutor;

  public ResourceIdsJob getJobById(String id) {
    var jobEntity = consortiumTenantExecutor.execute(() -> jobRepository.getReferenceById(id));
    return resourceIdsJobMapper.convert(jobEntity);
  }

  public ResourceIdsJob createStreamJob(ResourceIdsJob job, String tenantId) {
    log.debug("createStreamJob:: by [job: {}, tenantId: {}]", job, tenantId);
    var entity = resourceIdsJobMapper.convert(job);
    entity.setCreatedDate(new Date());
    entity.setStatus(StreamJobStatus.IN_PROGRESS);
    entity.setTemporaryTableName(generateTemporaryTableName());

    log.info("Attempts to create streamJob by [resourceIdsJob: {}]", entity);
    var savedJob = consortiumTenantExecutor.execute(() -> saveAndRun(entity, tenantId));

    return resourceIdsJobMapper.convert(savedJob);
  }

  /**
   * Provides an ability to stream prepared resource ids from the database using given request object.
   *
   * @param jobId - async jobs id with a prepared query
   * @return response with found resource ids using http streaming approach.
   */
  public ResponseEntity<Void> streamResourceIdsFromDb(String jobId) {
    log.debug("streamResourceIdsFromDb:: by [jobId: {}]", jobId);

    try {
      var httpServletResponse = prepareHttpResponse();
      httpServletResponse.setContentType(APPLICATION_JSON_VALUE);

      var outputStream = httpServletResponse.getOutputStream();
      consortiumTenantExecutor.run(() ->
        resourceIdService.streamResourceIdsAsJson(jobId, outputStream));
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

  private ResourceIdsJobEntity saveAndRun(ResourceIdsJobEntity entity, String tenantId) {
    var job = jobRepository.save(entity);
    streamIdsExecutor.execute(() -> resourceIdService.processResourceIdsJob(job, tenantId));
    return job;
  }

  private String generateTemporaryTableName() {
    return RandomStringUtils
      .random(32, 0, 0, true, false, null, new SecureRandom())
      .toLowerCase();
  }
}
