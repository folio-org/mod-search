package org.folio.search.client;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient("users")
public interface UsersClient {
  @GetMapping
  Users query(@RequestParam("query") String query);

  @PostMapping(consumes = APPLICATION_JSON_VALUE)
  void saveUser(@RequestBody User user);

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  class Users {
    @JsonAlias("total_records")
    private Integer totalRecords;
    @Singular
    private List<User> users = Collections.emptyList();
  }

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
