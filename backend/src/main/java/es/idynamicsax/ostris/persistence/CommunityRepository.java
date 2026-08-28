package es.idynamicsax.ostris.persistence;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
public interface CommunityRepository extends JpaRepository<CommunityEntity,UUID> {
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from CommunityEntity c where c.id=:id and c.tenantId=:tenantId") Optional<CommunityEntity> lock(UUID id, UUID tenantId);
}
