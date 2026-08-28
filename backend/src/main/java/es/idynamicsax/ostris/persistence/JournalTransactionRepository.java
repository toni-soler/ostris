package es.idynamicsax.ostris.persistence;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface JournalTransactionRepository extends JpaRepository<JournalTransactionEntity,UUID> {}
