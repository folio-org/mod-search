package org.folio.search.service.consortia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.folio.search.domain.dto.ResourceIdsJob;
import org.folio.search.service.ResourceIdsJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResourceIdsJobServiceDecoratorTest extends DecoratorBaseTest {

  @Mock
  private ConsortiaTenantExecutor consortiaTenantExecutor;
  @Mock
  private ResourceIdsJobService service;
  @InjectMocks
  private ResourceIdsJobServiceDecorator decorator;

  @BeforeEach
  void setUp() {
    mockExecutor(consortiaTenantExecutor);
  }

  @Test
  void getJobById() {
    var id = "test";
    var expected = new ResourceIdsJob();
    when(service.getJobById(id)).thenReturn(expected);

    var actual = decorator.getJobById(id);

    assertThat(actual).isEqualTo(expected);
    verify(service).getJobById(id);
    verify(consortiaTenantExecutor).execute(any());
  }

  @Test
  void createStreamJob() {
    var tenantId = "test";
    var expected = new ResourceIdsJob();
    when(service.createStreamJob(expected, tenantId)).thenReturn(expected);

    var actual = decorator.createStreamJob(expected, tenantId);

    assertThat(actual).isEqualTo(expected);
    verify(service).createStreamJob(expected, tenantId);
    verify(consortiaTenantExecutor).execute(any());
  }

}
