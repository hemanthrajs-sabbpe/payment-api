
package com.example.payment_api;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BusinessCategoryRepository
        extends JpaRepository<BusinessCategory, Long> {

    Optional<BusinessCategory> findByCategoryCode(String categoryCode);

    List<BusinessCategory> findByStatus(String status);

    boolean existsByCategoryCode(String categoryCode);
}
