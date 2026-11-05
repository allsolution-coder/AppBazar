package com.appbazaar.repo;

import com.appbazaar.model.LoginCode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginCodeRepository extends JpaRepository<LoginCode, Long> {
    Optional<LoginCode> findByEmail(String email);
}
