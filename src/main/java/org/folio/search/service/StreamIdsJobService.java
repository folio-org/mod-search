package org.folio.search.service;

import java.util.Date;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.model.streamids.StreamIdsJobEntity;
import org.folio.search.model.streamids.StreamJobStatus;
import org.folio.search.repository.StreamIdsJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@RequiredArgsConstructor
public class StreamIdsJobService {

  private final StreamIdsJobRepository jobRepository;

  @Transactional(readOnly = true)
  public StreamIdsJobEntity getJobById(String id) {
    return jobRepository.getById(id);
  }

  @Transactional
  public StreamIdsJobEntity createStreamJob(StreamIdsJobEntity entity) {
    entity.setId(UUID.randomUUID().toString());
    entity.setCreatedDate(new Date());
    entity.setStatus(StreamJobStatus.IN_PROGRESS);
    return jobRepository.save(entity);
  }
}
