package com.simplemdm.repository.system;

import com.simplemdm.model.system.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("select u from User u where u.system.id = :systemId and u.username = :username")
    Optional<User> findBySystemIdAndUsername(Long systemId, String username);
}
