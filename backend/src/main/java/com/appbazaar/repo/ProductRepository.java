package com.appbazaar.repo;

import com.appbazaar.model.*;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByStatusOrderByIdDesc(Status status);
    List<Product> findBySellerOrderByIdDesc(User seller);
    long countByStatus(Status status);
    long countByCategory(String category);

    @Query("""
        select p from Product p
        where p.status = com.appbazaar.model.Status.LIVE
          and (:cat = 'All' or p.category = :cat
               or (:cat = 'Projects' and p.category in ('College projects', 'Mini projects')))
          and (lower(p.name) like lower(concat('%', :q, '%'))
            or lower(p.description) like lower(concat('%', :q, '%')))
        order by p.id desc
        """)
    List<Product> search(@Param("q") String q, @Param("cat") String cat);
}
