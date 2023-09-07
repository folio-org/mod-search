package org.folio.search.controller;

import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;
import static org.folio.search.utils.SearchUtils.AUTHORITY_BROWSING_FIELD;
import static org.folio.search.utils.SearchUtils.AUTHORITY_RESOURCE;
import static org.folio.search.utils.SearchUtils.CALL_NUMBER_BROWSING_FIELD;
import static org.folio.search.utils.SearchUtils.CONTRIBUTOR_BROWSING_FIELD;
import static org.folio.search.utils.SearchUtils.CONTRIBUTOR_RESOURCE;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.SearchUtils.INSTANCE_SUBJECT_RESOURCE;
import static org.folio.search.utils.SearchUtils.SHELVING_ORDER_BROWSING_FIELD;
import static org.folio.search.utils.SearchUtils.SUBJECT_BROWSING_FIELD;
import static org.folio.search.utils.SearchUtils.TYPED_CALL_NUMBER_BROWSING_FIELD;

import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.AuthorityBrowseResult;
import org.folio.search.domain.dto.CallNumberBrowseResult;
import org.folio.search.domain.dto.CallNumberType;
import org.folio.search.domain.dto.ContributorBrowseResult;
import org.folio.search.domain.dto.SubjectBrowseResult;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.service.BrowseRequest;
import org.folio.search.model.service.BrowseRequest.BrowseRequestBuilder;
import org.folio.search.rest.resource.BrowseApi;
import org.folio.search.service.browse.AuthorityBrowseService;
import org.folio.search.service.browse.CallNumberBrowseService;
import org.folio.search.service.browse.ContributorBrowseService;
import org.folio.search.service.browse.SubjectBrowseService;
import org.folio.search.service.consortium.TenantProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/")
public class BrowseController implements BrowseApi {

  private final SubjectBrowseService subjectBrowseService;
  private final AuthorityBrowseService authorityBrowseService;
  private final CallNumberBrowseService callNumberBrowseService;
  private final ContributorBrowseService contributorBrowseService;
  private final TenantProvider tenantProvider;

  @Override
  public ResponseEntity<AuthorityBrowseResult> browseAuthorities(String query, String tenant,
                                                                 Integer limit, Boolean expandAll,
                                                                 Boolean highlightMatch,
                                                                 Integer precedingRecordsCount) {
    var browseRequest = getBrowseRequestBuilder(query, tenant, limit, expandAll, highlightMatch, precedingRecordsCount)
      .resource(AUTHORITY_RESOURCE).targetField(AUTHORITY_BROWSING_FIELD).build();
    var browseResult = authorityBrowseService.browse(browseRequest);
    return ResponseEntity.ok(new AuthorityBrowseResult()
      .items(browseResult.getRecords())
      .totalRecords(browseResult.getTotalRecords())
      .prev(browseResult.getPrev())
      .next(browseResult.getNext()));
  }

  @Override
  public ResponseEntity<CallNumberBrowseResult> browseInstancesByCallNumber(String query, String tenant,
                                                                            Integer limit, Boolean expandAll,
                                                                            Boolean highlightMatch,
                                                                            Integer precedingRecordsCount,
                                                                            CallNumberType callNumberType) {
    var browseRequest = getBrowseRequestBuilder(query, tenant, limit, expandAll, highlightMatch, precedingRecordsCount)
      .resource(INSTANCE_RESOURCE)
      .targetField(SHELVING_ORDER_BROWSING_FIELD)
      .subField(callNumberType == null ? CALL_NUMBER_BROWSING_FIELD : TYPED_CALL_NUMBER_BROWSING_FIELD)
      .refinedCondition(callNumberType != null ? callNumberType.getValue() : null)
      .build();

    var instanceByCallNumber = callNumberBrowseService.browse(browseRequest);
    return ResponseEntity.ok(new CallNumberBrowseResult()
      .items(instanceByCallNumber.getRecords())
      .totalRecords(instanceByCallNumber.getTotalRecords())
      .prev(instanceByCallNumber.getPrev())
      .next(instanceByCallNumber.getNext()));
  }

  @Override
  public ResponseEntity<ContributorBrowseResult> browseInstancesByContributor(String query, String tenant,
                                                                              Integer limit, Boolean highlightMatch,
                                                                              Integer precedingRecordsCount) {
    var browseRequest = getBrowseRequestBuilder(query, tenant, limit, null, highlightMatch, precedingRecordsCount)
      .resource(CONTRIBUTOR_RESOURCE).targetField(CONTRIBUTOR_BROWSING_FIELD).build();

    var browseResult = contributorBrowseService.browse(browseRequest);
    return ResponseEntity.ok(new ContributorBrowseResult()
      .items(browseResult.getRecords())
      .totalRecords(browseResult.getTotalRecords())
      .prev(browseResult.getPrev())
      .next(browseResult.getNext()));
  }

  @Override
  public ResponseEntity<SubjectBrowseResult> browseInstancesBySubject(String query, String tenant,
                                                                      Integer limit, Boolean highlightMatch,
                                                                      Integer precedingRecordsCount) {
    var browseRequest = getBrowseRequestBuilder(query, tenant, limit, null, highlightMatch, precedingRecordsCount)
      .resource(INSTANCE_SUBJECT_RESOURCE).targetField(SUBJECT_BROWSING_FIELD).build();

    var browseResult = subjectBrowseService.browse(browseRequest);
    return ResponseEntity.ok(new SubjectBrowseResult()
      .items(browseResult.getRecords())
      .totalRecords(browseResult.getTotalRecords())
      .prev(browseResult.getPrev())
      .next(browseResult.getNext()));
  }

  private BrowseRequestBuilder getBrowseRequestBuilder(String query, String tenant, Integer limit,
                                                              Boolean expandAll, Boolean highlightMatch,
                                                              Integer precedingRecordsCount) {
    if (precedingRecordsCount != null && precedingRecordsCount >= limit) {
      throw new RequestValidationException("Preceding records count must be less than request limit",
        "precedingRecordsCount", String.valueOf(precedingRecordsCount));
    }

    return BrowseRequest.builder()
      .limit(limit)
      .query(query)
      .tenantId(tenantProvider.getTenant(tenant))
      .expandAll(expandAll)
      .highlightMatch(highlightMatch)
      .precedingRecordsCount(defaultIfNull(precedingRecordsCount, limit / 2));
  }
}
