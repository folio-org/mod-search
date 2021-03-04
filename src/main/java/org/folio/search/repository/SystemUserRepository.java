package org.folio.search.repository;

import java.util.Optional;
import org.folio.search.model.SystemUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemUserRepository extends JpaRepository<SystemUser, String> {

  Optional<SystemUser> findOneByUsername(String username);
}
