package org.folio.search.service.systemuser;

import static org.folio.search.client.AuthnClient.UserCredentials;
import static org.folio.search.client.PermissionsClient.Permission;
import static org.folio.search.client.PermissionsClient.Permissions;
import static org.folio.search.client.UsersClient.User;
import static org.folio.spring.scope.FolioExecutionScopeExecutionContextManager.beginFolioExecutionContext;
import static org.folio.spring.scope.FolioExecutionScopeExecutionContextManager.endFolioExecutionContext;
import static org.springframework.util.CollectionUtils.isEmpty;

import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.folio.search.client.AuthnClient;
import org.folio.search.client.PermissionsClient;
import org.folio.search.client.UsersClient;
import org.folio.search.configuration.properties.FolioSystemUserProperties;
import org.folio.search.model.SystemUser;
import org.folio.search.repository.SystemUserRepository;
import org.folio.search.service.context.AnonymousUserFolioExecutionContext;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.integration.XOkapiHeaders;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ResourceUtils;

@Log4j2
@RequiredArgsConstructor
@Service
@EnableConfigurationProperties(FolioSystemUserProperties.class)
public class SystemUserService {
  private final PermissionsClient permissionsClient;
  private final UsersClient usersClient;
  private final AuthnClient authnClient;
  private final SystemUserRepository systemUserRepository;
  private final FolioSystemUserProperties folioSystemUserConf;

  public void prepareSystemUser(FolioExecutionContext context) {
    var systemUser = buildDefaultSystemUser(context);
    var folioUser = getFolioUser(systemUser.getUsername());

    if (folioUser.isPresent()) {
      addPermissions(folioUser.get().getId());
    } else {
      var userId = createFolioUser();
      saveCredentials(systemUser);
      assignPermissions(userId);
    }

    systemUserRepository.save(systemUser);
  }

  public SystemUser getSystemUser(String tenantId) {
    var systemUser = systemUserRepository.getByTenantId(tenantId)
      .orElseThrow(() -> new IllegalArgumentException("No system user for tenant " + tenantId));

    if (systemUser.hasNoToken()) {
      synchronized (SystemUserService.class) {
        if (systemUser.hasNoToken()) {
          var token = loginSystemUser(systemUser);
          systemUser.setOkapiToken(token);
          systemUserRepository.save(systemUser);
        }
      }
    }

    return systemUser;
  }

  private Optional<UsersClient.User> getFolioUser(String username) {
    var users = usersClient.query("username==" + username);
    return users.getUsers().stream().findFirst();
  }

  private String createFolioUser() {
    final var user = createUserObject();
    final var id = user.getId();
    usersClient.saveUser(user);
    return id;
  }

  private void saveCredentials(SystemUser parameters) {
    authnClient.saveCredentials(UserCredentials.of(parameters.getUsername(),
      folioSystemUserConf.getPassword()));

    log.info("Saved credentials for user: [{}]", parameters.getUsername());
  }

  private void assignPermissions(String userId) {
    List<String> perms = getResourceLines(folioSystemUserConf.getPermissionsFilePath());

    if (isEmpty(perms)) {
      throw new IllegalStateException("No permissions found to assign to user with id: " + userId);
    }

    var permissions = Permissions.of(UUID.randomUUID()
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
    permissionsToAdd.removeAll(assignedPermissions.getPermissions());

    permissionsToAdd.forEach(permission ->
      permissionsClient.addPermission(userId, Permission.of(permission)));
  }

  private User createUserObject() {
    final var user = new User();

    user.setId(UUID.randomUUID()
      .toString());
    user.setActive(true);
    user.setUsername(folioSystemUserConf.getUsername());

    user.setPersonal(new User.Personal());
    user.getPersonal()
      .setLastName(folioSystemUserConf.getLastname());

    return user;
  }

  private String loginSystemUser(SystemUser user) {
    try {
      beginFolioExecutionContext(new AnonymousUserFolioExecutionContext(
        user.getTenantId(), user.getOkapiUrl()));

      var response = authnClient.getApiKey(UserCredentials
        .of(user.getUsername(), folioSystemUserConf.getPassword()));

      return Optional.ofNullable(response.getHeaders().get(XOkapiHeaders.TOKEN))
        .filter(list -> !CollectionUtils.isEmpty(list))
        .map(list -> list.get(0))
        .orElseThrow(() -> new IllegalStateException(String.format("User [%s] cannot log in",
          user.getUsername())));

    } finally {
      endFolioExecutionContext();
    }
  }

  private SystemUser buildDefaultSystemUser(FolioExecutionContext context) {
    return SystemUser.builder()
      .id(UUID.randomUUID())
      .username(folioSystemUserConf.getUsername())
      .okapiUrl(context.getOkapiUrl())
      .tenantId(context.getTenantId()).build();
  }

  @SneakyThrows
  private static List<String> getResourceLines(String permissionsFilePath) {
    return Files.readAllLines(ResourceUtils.getFile(permissionsFilePath).toPath());
  }
}
