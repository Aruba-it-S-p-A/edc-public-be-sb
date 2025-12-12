package edc.repository;

import edc.entity.ParticipantUser;
import edc.entity.projection.ParticipantMeDto;
import edc.entity.Participant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


import java.util.List;
import java.util.Optional;

@Repository
public interface ParticipantRepository extends JpaRepository<Participant, Integer> {

    Optional<Participant> findByExternalId(String externalId);

    boolean existsByName(String name);


    @Query("SELECT p " +
           "FROM Participant p WHERE " +
           "(:currentOperation IS NULL OR p.currentOperation = :currentOperation) AND " +
           "(:participantName IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :participantName, '%')))")
    Page<Participant> findByFilters(@Param("currentOperation") String currentOperation,
                                              @Param("participantName") String participantName,
                                              Pageable pageable);

    @Query(value = "SELECT p.* FROM participants p " +
            "JOIN tenants t ON p.tenant_id = t.id " +
            "WHERE t.name = :tenantName AND t.deleted_at IS NULL AND " +
            "(:currentOperation IS NULL OR p.current_operation = :currentOperation) AND " +
            "(:participantName IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :participantName, '%')))",
            countQuery = "SELECT COUNT(*) FROM participants p " +
                    "JOIN tenants t ON p.tenant_id = t.id " +
                    "WHERE t.name = :tenantName ",
            nativeQuery = true)
    Page<Participant> findByFiltersAndTenantName(@Param("tenantName") String tenantName,
                                               @Param("currentOperation") String currentOperation,
                                               @Param("participantName") String participantName,
                                               Pageable pageable);


    @Query("""
    SELECT p
    FROM Participant p
    JOIN Tenant t ON p.tenantId = t.id
    WHERE p.externalId = :externalId
      AND t.name = :tenantName
    """)
    Optional<Participant> findByExternalIdAndTenantName(@Param("externalId") String externalId,
                                                        @Param("tenantName") String tenantName);

    @Query("""
    SELECT new edc.entity.projection.ParticipantMeDto(    
        p.externalId,
        p.name,
        p.companyName,
        p.currentOperation,
        p.did,
        p.host,
        p.description,
        p.metadata,
        p.createdAt,
        pu.externalId,
        pu.username,
        pu.metadata,
        pu.createdAt
    )
    FROM Participant p
    JOIN Tenant t ON p.tenantId = t.id
    JOIN ParticipantUser pu ON pu.participantId = p.id
    WHERE pu.username = :userName
      AND pu.status IN (:statusList)
      AND t.name = :tenantName
    """)
    Optional<ParticipantMeDto> findByUserNameAndTenantName(@Param("userName") String userName,
                                                           @Param("tenantName") String tenantName,
                                                           @Param("statusList") List<ParticipantUser.Status> statusList);


    @Query("""
    SELECT p
    FROM Participant p
    JOIN Tenant t ON p.tenantId = t.id
    JOIN ParticipantUser cu ON cu.participantId = p.id
    WHERE p.externalId = :externalId
      AND cu.username = :userName
      AND cu.status IN (:statusList)
      AND t.name = :tenantName
    """)
    Optional<Participant> findByExternalIdAndTenantNameAndUserName(@Param("externalId") String externalId,
                                                                   @Param("tenantName") String tenantName,
                                                                   @Param("userName") String userName,
                                                                   @Param("statusList") List<ParticipantUser.Status> statusList);


}
