package com.appbazaar.repo;

import com.appbazaar.model.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    long countByFeePaidAtNotNull();
}
