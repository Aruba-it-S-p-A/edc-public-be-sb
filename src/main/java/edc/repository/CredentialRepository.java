package edc.repository;

import edc.entity.Credential;
import edc.entity.ParticipantUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CredentialRepository extends JpaRepository<Credential, Integer> {

    Optional<Credential> findByExternalId(String externalId);


    @Query("SELECT c FROM Credential c WHERE c.participant.externalId = :participantExternalId AND " +
           "(:credentialStatus IS NULL OR c.status = :credentialStatus)")
    Page<Credential> findByParticipantExternalIdAndStatus(@Param("participantExternalId") String participantExternalId,
                                                         @Param("credentialStatus") Credential.CredentialStatus credentialStatus,
                                                         Pageable pageable);


    @Query(value = "SELECT c.* FROM credentials c " +
            "JOIN participants p ON c.participant_id = p.id " +
            "JOIN tenants t ON p.tenant_id = t.id " +
            "WHERE p.external_id = :participantExternalId AND t.name = :tenantName AND" +
            " (:credentialStatus IS NULL OR c.status = :credentialStatus)",
            countQuery = "SELECT COUNT(*) FROM credentials c " +
                    "JOIN participants p ON c.participant_id = p.id " +
                    "JOIN tenants t ON p.tenant_id = t.id " +
                    "WHERE p.external_id = :participantExternalId AND t.name = :tenantName AND" +
                    " (:credentialStatus IS NULL OR c.status = :credentialStatus)",
            nativeQuery = true)
    Page<Credential> findByParticipantExternalIdAndStatusAndTenantName(@Param("participantExternalId") String participantExternalId,
                                                                       @Param("credentialStatus") Credential.CredentialStatus credentialStatus,
                                                                        @Param("tenantName") String tenantName,
                                                                        Pageable pageable);

    @Query(value = "SELECT c.* FROM credentials c " +
            "JOIN participants p ON c.participant_id = p.id " +
            "JOIN tenants t ON p.tenant_id = t.id " +
            "JOIN participant_users pu ON pu.participant_id = p.id " +
            "WHERE p.external_id = :participantExternalId " +
            "AND t.name = :tenantName " +
            "AND pu.username = :userName " +
            "AND pu.status IN (:userStatusList)" +
            "AND (:credentialStatus IS NULL OR c.status = :credentialStatus)",
            countQuery = "SELECT COUNT(*) FROM credentials c " +
                    "JOIN participants p ON c.participant_id = p.id " +
                    "JOIN tenants t ON p.tenant_id = t.id " +
                    "JOIN participant_users pu ON pu.participant_id = p.id " +
                    "WHERE p.external_id = :participantExternalId " +
                    "AND t.name = :tenantName " +
                    "AND pu.username = :userName " +
                    "AND pu.status IN (:userStatusList)" +
                    "AND (:credentialStatus IS NULL OR c.status = :credentialStatus)",
            nativeQuery = true)
    Page<Credential> findByParticipantExternalIdAndUserNameAndStatusAndTenantName(@Param("participantExternalId") String participantExternalId,
                                                                                  @Param("userName") String userName,
                                                                                  @Param("userStatusList") List<ParticipantUser.Status> userStatusList,
                                                                                   @Param("credentialStatus") Credential.CredentialStatus credentialStatus,
                                                                                   @Param("tenantName") String tenantName,
                                                                                   Pageable pageable);

}
