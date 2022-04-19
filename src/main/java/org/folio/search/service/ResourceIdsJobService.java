package org.folio.search.service;

import java.util.Date;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.RandomStringUtils;
import org.folio.search.converter.ResourceIdsJobMapper;
import org.folio.search.domain.dto.ResourceIdsJob;
import org.folio.search.model.types.StreamJobStatus;
import org.folio.search.repository.ResourceIdsJobRepository;
import org.folio.search.repository.ResourceIdsTemporaryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@RequiredArgsConstructor
public class ResourceIdsJobService {

  private final ResourceIdsJobRepository jobRepository;
  private final ResourceIdsTemporaryRepository idsTemporaryRepository;
  private final ResourceIdsJobMapper resourceIdsJobMapper;

  @Transactional(readOnly = true)
  public ResourceIdsJob getJobById(String id) {
    return resourceIdsJobMapper.convert(jobRepository.getById(id));
  }

  @Transactional
  public ResourceIdsJob createStreamJob(ResourceIdsJob job) {
    var tableName = RandomStringUtils.randomAlphabetic(32).toLowerCase();
    var entity = resourceIdsJobMapper.convert(job);
    entity.setCreatedDate(new Date());
    entity.setStatus(StreamJobStatus.IN_PROGRESS);
    entity.setTemporaryTableName(tableName);
    idsTemporaryRepository.createTableForIds(tableName);
    var result = jobRepository.save(entity);
    return resourceIdsJobMapper.convert(result);
  }
}
