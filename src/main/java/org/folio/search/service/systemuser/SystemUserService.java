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
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.folio.search.client.AuthnClient;
import org.folio.search.client.PermissionsClient;
import org.folio.search.client.UsersClient;
import org.folio.search.configuration.properties.FolioSystemUserProperties;
import org.folio.search.model.SystemUser;
import org.folio.search.repository.SystemUserRepository;
import org.folio.search.repository.SystemUserTokenCache;
import org.folio.search.service.context.FolioExecutionContextBuilder;
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
  private final FolioExecutionContextBuilder contextBuilder;
  private final FolioSystemUserProperties folioSystemUserConf;
  private final SystemUserTokenCache tokenCache;

  public void prepareSystemUser(FolioExecutionContext context) {
    var folioUser = getFolioUser(folioSystemUserConf.getUsername());
    var userId = folioUser.map(User::getId)
      .orElse(UUID.randomUUID().toString());

    if (folioUser.isPresent()) {
      addPermissions(userId);
    } else {
      createFolioUser(userId);
      saveCredentials();
      assignPermissions(userId);
    }

    systemUserRepository.save(SystemUser.builder()
      .id(userId)
      .username(folioSystemUserConf.getUsername())
      .tenantId(context.getTenantId())
      .okapiUrl(context.getOkapiUrl())
      .build());
  }

  public SystemUser getSystemUser(String tenantId) {
    if (tokenCache.hasTokenForTenant(tenantId)) {
      return tokenCache.getByTenant(tenantId);
    }

    return issueTokenForSystemUser(tenantId);
  }

  private synchronized SystemUser issueTokenForSystemUser(String tenantId) {
    log.info("Attempting to issue token for system user...");
    if (tokenCache.hasTokenForTenant(tenantId)) {
      log.info("Token is already issued");
      return tokenCache.getByTenant(tenantId);
    }

    var systemUser = findSystemUser(tenantId)
      .orElseThrow(() -> new IllegalStateException("There is no system user configured"));

    return executeTenantScoped(contextBuilder.forSystemUser(systemUser), () -> {
      var token = loginSystemUser(systemUser);
      log.info("Token for system user has been issued");
      return tokenCache.save(systemUser.getTenantId(), systemUser.withToken(token));
    });
  }

  private Optional<SystemUser> findSystemUser(String tenantId) {
    return executeTenantScoped(contextBuilder.dbOnlyContext(tenantId),
      () -> systemUserRepository.findOneByUsername(folioSystemUserConf.getUsername()));
  }

  private Optional<UsersClient.User> getFolioUser(String username) {
    var users = usersClient.query("username==" + username);
    return users.getUsers().stream().findFirst();
  }

  private void createFolioUser(String id) {
    final var user = createUserObject(id);
    usersClient.saveUser(user);
  }

  private void saveCredentials() {
    authnClient.saveCredentials(UserCredentials.of(folioSystemUserConf.getUsername(),
      folioSystemUserConf.getPassword()));

    log.info("Saved credentials for user: [{}]", folioSystemUserConf.getUsername());
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

  private User createUserObject(String id) {
    final var user = new User();

    user.setId(id);
    user.setActive(true);
    user.setUsername(folioSystemUserConf.getUsername());

    user.setPersonal(new User.Personal());
    user.getPersonal()
      .setLastName(folioSystemUserConf.getLastname());

    return user;
  }

  private String loginSystemUser(SystemUser systemUser) {
    var response = authnClient.getApiKey(UserCredentials
      .of(systemUser.getUsername(), folioSystemUserConf.getPassword()));

    return Optional.ofNullable(response.getHeaders().get(XOkapiHeaders.TOKEN))
      .filter(list -> !CollectionUtils.isEmpty(list))
      .map(list -> list.get(0))
      .orElseThrow(() -> new IllegalStateException(String.format("User [%s] cannot log in",
        systemUser.getUsername())));
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
    return Files.readAllLines(ResourceUtils.getFile(permissionsFilePath).toPath());
  }
}
