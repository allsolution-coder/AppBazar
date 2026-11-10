package com.appbazaar.repo;

import com.appbazaar.model.*;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Orders, Long> {
    List<Orders> findByBuyerOrderByIdDesc(User buyer);
    Optional<Orders> findByPaymentRef(String paymentRef);
    long countByBuyer(User buyer);

    @Query("select count(o) > 0 from Orders o join o.items i where o.buyer = :buyer and i.id = :productId")
    boolean hasPurchased(@Param("buyer") User buyer, @Param("productId") Long productId);

    @Query("select coalesce(sum(o.total), 0) from Orders o")
    long totalRevenue();
}
