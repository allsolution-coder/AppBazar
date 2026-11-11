package com.appbazaar.repo;

import com.appbazaar.model.ManualPayment;
import com.appbazaar.model.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManualPaymentRepository extends JpaRepository<ManualPayment, Long> {
    List<ManualPayment> findByUserOrderByIdDesc(User user);
    List<ManualPayment> findByStatusOrderByIdAsc(String status);
    long countByUserAndStatus(User user, String status);
    boolean existsByUtr(String utr);
}
