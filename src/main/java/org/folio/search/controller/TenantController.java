package org.folio.search.controller;

import lombok.RequiredArgsConstructor;
import org.folio.tenant.rest.resource.TenantApi;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping(value = "/_/")
@RestController("folioTenantController")
public class TenantController implements TenantApi {
}
