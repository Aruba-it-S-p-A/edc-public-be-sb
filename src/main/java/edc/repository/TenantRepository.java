package edc.repository;

import edc.entity.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Integer> {

    Optional<Tenant> findByExternalId(String externalId);

    Optional<Tenant> findByName(String name);

    boolean existsByName(String name);


    @Query("SELECT t FROM Tenant t WHERE t.status != 'DELETED'")
    Page<Tenant> findAllActive(Pageable pageable);

    @Query("SELECT t FROM Tenant t WHERE t.status = :status")
    Page<Tenant> findByStatus(@Param("status") Tenant.TenantStatus status, Pageable pageable);

    @Query("SELECT t FROM Tenant t WHERE t.name LIKE %:name% AND t.status != 'DELETED'")
    Page<Tenant> findByNameContainingIgnoreCase(@Param("name") String name, Pageable pageable);
}
