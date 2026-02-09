package abl.frd.mgchecker.repository;

import abl.frd.mgchecker.enumpack.ReconStatus;
import abl.frd.mgchecker.enumpack.SourceType;
import abl.frd.mgchecker.model.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<TransactionEntity, Integer> {
    List<TransactionEntity> findByReconStatusAndSourceType(
            ReconStatus status, SourceType sourceType);
    // JPQL query for exact matches
    @Query("SELECT p.transactionNo, s.transactionNo FROM TransactionEntity p JOIN TransactionEntity s ON p.transactionNo = s.transactionNo AND p.amount = s.amount AND p.legacyId = s.legacyId WHERE p.sourceType = :paymentType AND s.sourceType = :settlementType AND p.reconStatus = :status AND s.reconStatus = :status")
    List<Object[]> findExactMatches(
            @Param("paymentType") SourceType paymentType,
            @Param("settlementType") SourceType settlementType,
            @Param("status") ReconStatus status
    );
    List<TransactionEntity> findByReconStatus(ReconStatus status);
    @Modifying
    @Query("UPDATE TransactionEntity t SET t.reconStatus = :status WHERE t.transactionNo = :id")
    void updateStatus(@Param("id") String id, @Param("status") ReconStatus status);
    List<TransactionEntity> findByTransactionNoAndAndAmountAndTransactionDateAndSourceTypeAndReconStatus(
            String transactionNo,
            Double amount,
            String transactionDate,
            SourceType sourceType,
            ReconStatus reconStatus
    );
    TransactionEntity findByTransactionNo(String transactionNo);
}
