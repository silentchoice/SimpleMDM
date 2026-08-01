package com.simplemdm.repository.system;

import com.simplemdm.model.system.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Optional;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = {"system", "department"})
    @Query("select u from User u where u.id = :id")
    Optional<User> findWithContextById(Long id);

    @Query("select u from User u where u.system.id = :systemId and u.username = :username")
    Optional<User> findBySystemIdAndUsername(Long systemId, String username);

    List<User> findByUsername(String username);

    @Query("select u from User u where u.system.id = :systemId and u.status = :status order by u.id")
    List<User> findBySystemIdAndStatusOrderById(Long systemId, String status);
}
