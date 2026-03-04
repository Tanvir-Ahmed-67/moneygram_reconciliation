package abl.frd.mgchecker.repository;

import abl.frd.mgchecker.enumpack.SourceType;
import abl.frd.mgchecker.model.ReconciliationUnmatched;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ReconciliationUnmatchedRepository extends JpaRepository<ReconciliationUnmatched, Long> {
    boolean existsByTransactionNoAndSourceType(
            String transactionNo,
            SourceType sourceType
    );
    @Modifying
    @Transactional
    @Query("DELETE FROM ReconciliationUnmatched u WHERE u.transactionNo IN :txnNumbers")
    void deleteByTransactionNoIn(@Param("txnNumbers") List<String> txnNumbers);
}
