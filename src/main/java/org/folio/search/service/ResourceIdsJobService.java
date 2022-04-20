package org.folio.search.service;

import static org.folio.search.model.service.CqlResourceIdsRequest.AUTHORITY_ID_PATH;
import static org.folio.search.utils.SearchUtils.AUTHORITY_RESOURCE;

import java.util.Date;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.RandomStringUtils;
import org.folio.search.converter.ResourceIdsJobMapper;
import org.folio.search.domain.dto.ResourceIdsJob;
import org.folio.search.model.service.CqlResourceIdsRequest;
import org.folio.search.model.streamids.ResourceIdsJobEntity;
import org.folio.search.model.types.StreamJobStatus;
import org.folio.search.repository.ResourceIdsJobRepository;
import org.folio.search.repository.ResourceIdsTemporaryRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@RequiredArgsConstructor
public class ResourceIdsJobService {

  private final ResourceIdsJobRepository jobRepository;
  private final ResourceIdsTemporaryRepository idsTemporaryRepository;
  private final ResourceIdsJobMapper resourceIdsJobMapper;
  private final ResourceIdService resourceIdService;

  @Transactional(readOnly = true)
  public ResourceIdsJob getJobById(String id) {
    return resourceIdsJobMapper.convert(jobRepository.getById(id));
  }

  @Transactional
  public ResourceIdsJob createStreamJob(ResourceIdsJob job, String tenantId) {
    var tableName = RandomStringUtils.randomAlphabetic(32).toLowerCase();
    var entity = resourceIdsJobMapper.convert(job);
    entity.setCreatedDate(new Date());
    entity.setStatus(StreamJobStatus.IN_PROGRESS);
    entity.setTemporaryTableName(tableName);
    idsTemporaryRepository.createTableForIds(tableName);
    var result = jobRepository.save(entity);
    runResourceStreamIds(entity, tenantId);
    return resourceIdsJobMapper.convert(result);
  }

  @Async
  public void runResourceStreamIds(ResourceIdsJobEntity job, String tenantId) {
    try {
      var request = CqlResourceIdsRequest
        .of(AUTHORITY_RESOURCE, tenantId, job.getQuery(), AUTHORITY_ID_PATH);
      var tableName = job.getTemporaryTableName();
      resourceIdService.streamResourceIds(request, idsList -> {
        try {
          idsTemporaryRepository.insertId(idsList, tableName);
        } catch (Exception e) {
          job.setStatus(StreamJobStatus.ERROR);
          jobRepository.save(job);
        }
      });
      job.setStatus(StreamJobStatus.COMPLETED);
      jobRepository.save(job);
    } catch (Exception e) {
      job.setStatus(StreamJobStatus.ERROR);
      jobRepository.save(job);
    }
  }
}
