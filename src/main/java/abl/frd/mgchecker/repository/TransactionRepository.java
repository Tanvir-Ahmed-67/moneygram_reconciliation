package abl.frd.mgchecker.repository;

import abl.frd.mgchecker.enumpack.ReconStatus;
import abl.frd.mgchecker.enumpack.SourceType;
import abl.frd.mgchecker.model.TransactionEntity;
import abl.frd.mgchecker.model.UploadedFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<TransactionEntity, Integer> {
    List<TransactionEntity> findByReconStatusAndSourceType(
            ReconStatus status, SourceType sourceType);
    // JPQL query for exact matches
    @Query("SELECT p.transactionNo, s.transactionNo FROM TransactionEntity p JOIN TransactionEntity s ON p.transactionNo = s.transactionNo AND p.referenceNo = s.referenceNo AND p.originatingCountry = s.originatingCountry AND p.amount = s.amount AND p.legacyId = s.legacyId AND p.transactionDate = s.transactionDate WHERE p.sourceType = :paymentType AND s.sourceType = :settlementType AND p.reconStatus = :status AND s.reconStatus = :status")
    List<Object[]> findExactMatches(
            @Param("paymentType") SourceType paymentType,
            @Param("settlementType") SourceType settlementType,
            @Param("status") ReconStatus status
    );
    List<TransactionEntity> findByReconStatusAndUploadedFileIdIn(ReconStatus status, List<Integer> fileIds);
    List<TransactionEntity> findByReconStatus(ReconStatus status);
    @Modifying
    @Query("UPDATE TransactionEntity t SET t.reconStatus = :status WHERE t.transactionNo = :id")
    void updateStatus(@Param("id") String id, @Param("status") ReconStatus status);
    List<TransactionEntity> findByTransactionNoAndAndAmountAndTransactionDateAndSourceTypeAndReconStatus(
            String transactionNo,
            BigDecimal amount,
            String transactionDate,
            SourceType sourceType,
            ReconStatus reconStatus
    );
    TransactionEntity findByTransactionNo(String transactionNo);
    Optional<TransactionEntity> findByTransactionNoAndSourceType(
            String transactionNo,
            SourceType sourceType
    );
    List<TransactionEntity> findByTransactionNoAndReferenceNoAndOriginatingCountryAndAmountAndTransactionDateAndReconStatus(
            String transactionNo,
            String referenceNo,
            String originatingCountry,
            BigDecimal amount,
            String transactionDate,
            ReconStatus reconStatus
    );
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM TransactionEntity t JOIN ReconciliationUnmatched u ON t.transactionNo = u.transactionNo AND t.sourceType = u.sourceType ")
    BigDecimal sumAllUnmatchedAmount();
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM TransactionEntity t JOIN ReconciliationUnmatched u ON t.transactionNo = u.transactionNo AND t.sourceType = u.sourceType WHERE t.fileUploadDate = :today")
    BigDecimal sumTodayUnmatchedAmount(@Param("today") String today);
    void deleteByUploadedFile(UploadedFileEntity uploadedFileEntity);
    List<TransactionEntity> findByUploadedFile(UploadedFileEntity uploadedFileEntity);
    void deleteByUploadedFileAndReconStatus(UploadedFileEntity file, ReconStatus status);
    boolean existsByTransactionNoAndReferenceNoAndAmountAndLegacyIdAndTransactionDateAndSourceType(String transactionNo,String referenceNo,BigDecimal amount,String legacyId,String paidDate,SourceType sourceType);
    List<TransactionEntity> findByReconStatusIn(List<ReconStatus> statuses);
}
