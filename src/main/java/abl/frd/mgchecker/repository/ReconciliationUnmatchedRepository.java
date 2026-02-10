package abl.frd.mgchecker.repository;

import abl.frd.mgchecker.enumpack.SourceType;
import abl.frd.mgchecker.model.ReconciliationUnmatched;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationUnmatchedRepository extends JpaRepository<ReconciliationUnmatched, Long> {
    boolean existsByTransactionNoAndSourceType(
            String transactionNo,
            SourceType sourceType
    );
}
