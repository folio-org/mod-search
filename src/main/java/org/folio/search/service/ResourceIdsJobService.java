package org.folio.search.service;

import java.security.SecureRandom;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.RandomStringUtils;
import org.folio.search.converter.ResourceIdsJobMapper;
import org.folio.search.domain.dto.ResourceIdsJob;
import org.folio.search.model.streamids.ResourceIdsJobEntity;
import org.folio.search.model.types.StreamJobStatus;
import org.folio.search.repository.ResourceIdsJobRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@RequiredArgsConstructor
public class ResourceIdsJobService {

  private final ResourceIdsJobRepository jobRepository;
  private final ResourceIdsJobMapper resourceIdsJobMapper;
  private final ResourceIdService resourceIdService;

  @Transactional(readOnly = true)
  public ResourceIdsJob getJobById(String id) {
    return resourceIdsJobMapper.convert(jobRepository.getById(id));
  }

  @Async
  public void startAsyncStreamingIdsJob(ResourceIdsJobEntity job, String tenantId) {
    resourceIdService.streamResourceIdsForJob(job, tenantId);
  }

  @Transactional
  public ResourceIdsJob createStreamJob(ResourceIdsJob job, String tenantId) {
    var entity = resourceIdsJobMapper.convert(job);
    entity.setCreatedDate(new Date());
    entity.setStatus(StreamJobStatus.IN_PROGRESS);
    entity.setTemporaryTableName(generateTemporaryTableName());

    var savedJob = jobRepository.save(entity);
    startAsyncStreamingIdsJob(savedJob, tenantId);
    return resourceIdsJobMapper.convert(savedJob);
  }

  private String generateTemporaryTableName() {
    return RandomStringUtils
      .random(32, 0, 0, true, false, null, new SecureRandom())
      .toLowerCase();
  }
}
