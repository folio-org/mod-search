package org.folio.search.service;

import java.security.SecureRandom;
import java.util.Date;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.RandomStringUtils;
import org.folio.search.converter.ResourceIdsJobMapper;
import org.folio.search.domain.dto.ResourceIdsJob;
import org.folio.search.model.streamids.ResourceIdsJobEntity;
import org.folio.search.model.types.StreamJobStatus;
import org.folio.search.repository.ResourceIdsJobRepository;
import org.folio.search.service.consortium.ConsortiumTenantExecutor;
import org.springframework.stereotype.Service;

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

  private ResourceIdsJobEntity saveAndRun(ResourceIdsJobEntity entity, String tenantId) {
    var job = jobRepository.save(entity);
    streamIdsExecutor.execute(() -> resourceIdService.streamResourceIdsForJob(job, tenantId));
    return job;
  }

  private String generateTemporaryTableName() {
    return RandomStringUtils
      .random(32, 0, 0, true, false, null, new SecureRandom())
      .toLowerCase();
  }
}
