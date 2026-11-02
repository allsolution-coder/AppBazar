package com.appbazaar.repo;

import com.appbazaar.model.Review;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByProductIdOrderByIdDesc(Long productId);
    Optional<Review> findByProductIdAndUserId(Long productId, Long userId);
    long countByProductId(Long productId);

    @Query("select avg(r.rating) from Review r where r.product.id = :pid")
    Double avgRating(@Param("pid") Long pid);
}
