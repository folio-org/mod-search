package org.folio.search.client;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.folio.search.model.service.ResultList;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient("users")
public interface UsersClient {
  @GetMapping
  ResultList<User> query(@RequestParam("query") String query);

  @PostMapping(consumes = APPLICATION_JSON_VALUE)
  void saveUser(@RequestBody User user);

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  class User {
    private String id;
    private String username;
    private boolean active;
    private Personal personal;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Personal {
      private String lastName;
    }
  }
}
