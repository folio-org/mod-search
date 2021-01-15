package org.folio.search.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.tenant.rest.resource.TenantApi;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping(value = "/_/")
@RestController("folioTenantController")
public class TenantController implements TenantApi {
}
