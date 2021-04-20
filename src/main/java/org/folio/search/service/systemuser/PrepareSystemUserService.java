package org.folio.search.service.systemuser;

import static org.folio.spring.scope.FolioExecutionScopeExecutionContextManager.beginFolioExecutionContext;
import static org.folio.spring.scope.FolioExecutionScopeExecutionContextManager.endFolioExecutionContext;
import static org.springframework.util.CollectionUtils.isEmpty;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;
import org.folio.search.client.AuthnClient;
import org.folio.search.client.PermissionsClient;
import org.folio.search.client.UsersClient;
import org.folio.search.configuration.properties.FolioSystemUserProperties;
import org.folio.search.model.SystemUser;
import org.folio.search.service.context.FolioExecutionContextBuilder;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.integration.XOkapiHeaders;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Log4j2
@RequiredArgsConstructor
@Service
@EnableConfigurationProperties(FolioSystemUserProperties.class)
public class PrepareSystemUserService {
  private final PermissionsClient permissionsClient;
  private final UsersClient usersClient;
  private final AuthnClient authnClient;
  private final FolioExecutionContextBuilder contextBuilder;
  private final FolioSystemUserProperties folioSystemUserConf;

  public void setupSystemUser() {
    var folioUser = getFolioUser(folioSystemUserConf.getUsername());
    var userId = folioUser.map(UsersClient.User::getId)
      .orElse(UUID.randomUUID().toString());

    if (folioUser.isPresent()) {
      log.info("System user already exists");
      addPermissions(userId);
    } else {
      log.info("No system user exist, creating...");

      createFolioUser(userId);
      saveCredentials();
      assignPermissions(userId);
    }
  }

  public String loginSystemUser(SystemUser systemUser) {
    return executeTenantScoped(contextBuilder.forSystemUser(systemUser), () -> {
      var response = authnClient.getApiKey(AuthnClient.UserCredentials
        .of(systemUser.getUsername(), folioSystemUserConf.getPassword()));

      return Optional.ofNullable(response.getHeaders().get(XOkapiHeaders.TOKEN))
        .filter(list -> !CollectionUtils.isEmpty(list))
        .map(list -> list.get(0))
        .orElseThrow(() -> new IllegalStateException(String.format("User [%s] cannot log in",
          systemUser.getUsername())));
    });
  }

  private Optional<UsersClient.User> getFolioUser(String username) {
    var users = usersClient.query("username==" + username);
    return users.getResult().stream().findFirst();
  }

  private void createFolioUser(String id) {
    final var user = createUserObject(id);
    usersClient.saveUser(user);
  }

  private void saveCredentials() {
    authnClient.saveCredentials(AuthnClient.UserCredentials.of(folioSystemUserConf.getUsername(),
      folioSystemUserConf.getPassword()));

    log.info("Saved credentials for user: [{}]", folioSystemUserConf.getUsername());
  }

  private void assignPermissions(String userId) {
    List<String> perms = getResourceLines(folioSystemUserConf.getPermissionsFilePath());

    if (isEmpty(perms)) {
      throw new IllegalStateException("No permissions found to assign to user with id: " + userId);
    }

    var permissions = PermissionsClient.Permissions.of(UUID.randomUUID()
      .toString(), userId, perms);

    permissionsClient.assignPermissionsToUser(permissions);
  }

  private void addPermissions(String userId) {
    var expectedPermissions = getResourceLines(folioSystemUserConf.getPermissionsFilePath());
    var assignedPermissions = permissionsClient.getUserPermissions(userId);

    if (isEmpty(expectedPermissions)) {
      throw new IllegalStateException("No permissions found to assign to user with id: " + userId);
    }

    var permissionsToAdd = new HashSet<>(expectedPermissions);
    assignedPermissions.getResult().forEach(permissionsToAdd::remove);

    permissionsToAdd.forEach(permission ->
      permissionsClient.addPermission(userId, PermissionsClient.Permission.of(permission)));
  }

  private UsersClient.User createUserObject(String id) {
    final var user = new UsersClient.User();

    user.setId(id);
    user.setActive(true);
    user.setUsername(folioSystemUserConf.getUsername());

    user.setPersonal(new UsersClient.User.Personal());
    user.getPersonal()
      .setLastName(folioSystemUserConf.getLastname());

    return user;
  }

  private <T> T executeTenantScoped(FolioExecutionContext context, Supplier<T> job) {
    try {
      beginFolioExecutionContext(context);
      return job.get();
    } finally {
      endFolioExecutionContext();
    }
  }

  @SneakyThrows
  private static List<String> getResourceLines(String permissionsFilePath) {
    ClassPathResource resource = new ClassPathResource(permissionsFilePath);
    return IOUtils.readLines(resource.getInputStream(), StandardCharsets.UTF_8);
  }
}
