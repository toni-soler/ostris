package es.idynamicsax.ostris.persistence;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
public interface AccountRepository extends JpaRepository<AccountEntity,UUID> {
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select a from AccountEntity a where a.tenantId=:tenantId and a.id in :ids order by a.id") List<AccountEntity> lockAll(UUID tenantId, Collection<UUID> ids);
}
