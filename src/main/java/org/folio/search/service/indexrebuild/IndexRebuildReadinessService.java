package org.folio.search.service.indexrebuild;

import static org.folio.search.utils.SearchUtils.TENANT_ID_FIELD_NAME;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.service.indexrebuild.jdbc.IndexRebuildStateRepository;
import org.folio.search.service.indexrebuild.model.IndexRebuildState;
import org.folio.search.service.indexrebuild.model.IndexRebuildStatus;
import org.springframework.stereotype.Service;

/**
 * Decides whether an index is up to date and whether a run is allowed to start.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class IndexRebuildReadinessService {

  private final IndexRebuildStateRepository stateRepository;
  private final IndexFingerprintService fingerprintService;

  /**
   * Checks that dependencies are ready for the whole scope. A single tenant rerun doesn't recreate the index, so it
   * can't apply a new mapping and is allowed only if the index is already on the current version.
   *
   * @throws RequestValidationException if the run can't be started
   */
  public void validateCanStart(IndexRebuildTarget target, IndexRebuildScope scope) {
    validateDependenciesReady(target.getDependsOn(), scope.tenants());
    validateNoDependentRunActive(target);
    if (!scope.wholeIndex() && !isOnCurrentVersion(target)) {
      log.warn("validateCanStart:: rejected, index is outdated for a single tenant run [resource: {}, tenant: {}]",
        target.getResource(), scope.tenants().getFirst());
      throw new RequestValidationException(
        "Index %s is outdated, run the reindex for the whole consortium first".formatted(target.getResource()),
        TENANT_ID_FIELD_NAME, scope.tenants().getFirst());
    }
  }

  /**
   * Checks that a failed or interrupted run can be resumed: dependencies have to be ready, and no run of a dependent
   * index can be active, the same as for a new run.
   *
   * @throws RequestValidationException if the run can't be resumed
   */
  public void validateCanResume(IndexRebuildTarget target, List<String> tenants) {
    validateDependenciesReady(target.getDependsOn(), tenants);
    validateNoDependentRunActive(target);
  }

  public boolean isReady(IndexRebuildState state, String fingerprint) {
    return state != null && state.status() == IndexRebuildStatus.COMPLETED && fingerprint.equals(state.fingerprint());
  }

  /**
   * Finds which of the given dependencies aren't ready for the tenant.
   */
  public List<IndexRebuildTarget> findBlockedBy(List<IndexRebuildTarget> dependencies, String tenantId) {
    return dependencies.stream()
      .filter(dependency -> !isReady(statesByTenant(dependency).get(tenantId), fingerprintService.current(dependency)))
      .toList();
  }

  void validateDependenciesReady(List<IndexRebuildTarget> dependencies, List<String> tenants) {
    for (var tenant : tenants) {
      var blockedBy = findBlockedBy(dependencies, tenant);
      if (!blockedBy.isEmpty()) {
        log.warn("validateDependenciesReady:: rejected, dependencies are not ready [tenant: {}, blockedBy: {}]",
          tenant, blockedBy);
        throw new RequestValidationException(
          "Reindex of dependencies has to be completed first: %s".formatted(blockedBy), TENANT_ID_FIELD_NAME, tenant);
      }
    }
  }

  void validateNoDependentRunActive(IndexRebuildTarget target) {
    Arrays.stream(IndexRebuildTarget.values())
      .filter(dependent -> dependent.getDependsOn().contains(target))
      .filter(stateRepository::isAnyActive)
      .findFirst()
      .ifPresent(dependent -> {
        log.warn("validateNoDependentRunActive:: rejected, a run of a dependent index is active "
                 + "[resource: {}, dependent: {}]", target.getResource(), dependent.getResource());
        throw new RequestValidationException("Reindex of a dependent index is in progress: " + dependent.getResource(),
          "resource", target.getResource());
      });
  }

  private boolean isOnCurrentVersion(IndexRebuildTarget target) {
    var fingerprint = fingerprintService.current(target);
    return stateRepository.findAll(target).stream().anyMatch(state -> isReady(state, fingerprint));
  }

  private Map<String, IndexRebuildState> statesByTenant(IndexRebuildTarget target) {
    return stateRepository.findAll(target).stream()
      .collect(Collectors.toMap(IndexRebuildState::tenantId, Function.identity()));
  }
}
