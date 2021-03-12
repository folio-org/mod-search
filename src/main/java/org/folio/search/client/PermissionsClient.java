package org.folio.search.client;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.folio.search.model.service.ResultList;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient("perms/users")
public interface PermissionsClient {

  @PostMapping(consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
  void assignPermissionsToUser(@RequestBody Permissions permissions);

  @PostMapping(value = "/{userId}/permissions?indexField=userId", consumes = APPLICATION_JSON_VALUE)
  void addPermission(@PathVariable("userId") String userId, Permission permission);

  @GetMapping(value = "/{userId}/permissions?indexField=userId")
  ResultList<String> getUserPermissions(@PathVariable("userId") String userId);

  @Data
  @AllArgsConstructor(staticName = "of")
  @NoArgsConstructor
  class Permission {
    private String permissionName;
  }

  @Data
  @AllArgsConstructor(staticName = "of")
  @NoArgsConstructor
  class Permissions {
    private String id;
    private String userId;
    @JsonProperty("permissions")
    private List<String> allowedPermissions = Collections.emptyList();
  }
}
