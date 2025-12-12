package edc.repository;

import edc.entity.Operation;
import edc.entity.Participant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OperationRepository extends JpaRepository<Operation, Integer> {


    @Query("SELECT o FROM Operation o WHERE o.participant = :participant AND " +
           "(:eventType IS NULL OR o.eventType = :eventType)")
    Page<Operation> findByParticipantAndEventType(@Param("participant") Participant participant, 
                                                 @Param("eventType") Operation.EventType eventType, 
                                                 Pageable pageable);


    boolean existsByExternalId(String externalId);

    @Query("SELECT o FROM Operation o WHERE o.participant = :participant ORDER BY o.createdAt DESC")
    List<Operation> findLatestByParticipant(@Param("participant") Participant participant, Pageable pageable);
}
