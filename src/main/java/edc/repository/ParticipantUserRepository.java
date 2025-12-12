package edc.repository;

import edc.entity.ParticipantUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ParticipantUserRepository extends JpaRepository<ParticipantUser, Integer> {

    boolean existsByUsername(String username);

    List<ParticipantUser> findByParticipantId(Integer participantId);

}
