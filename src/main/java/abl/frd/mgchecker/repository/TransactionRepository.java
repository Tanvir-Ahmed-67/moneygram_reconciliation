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
import java.util.Set;

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
    boolean existsByTransactionNoAndReferenceNoAndOriginatingCountryAndAmountAndTransactionDateAndSourceTypeAndLegacyId(
            String transactionNo,
            String referenceNo,
            String originatingCountry,
            BigDecimal amount,
            String transactionDate,
            SourceType sourceType,
            String legacyId
    );
    @Modifying
    @Query("UPDATE TransactionEntity t SET t.reconStatus = 'S' WHERE t.reconStatus IN ('M', 'U')")
    void resetAllToStaged();
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM TransactionEntity t JOIN ReconciliationUnmatched u ON t.transactionNo = u.transactionNo AND t.sourceType = u.sourceType ")
    BigDecimal sumAllUnmatchedAmount();
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM TransactionEntity t JOIN ReconciliationUnmatched u ON t.transactionNo = u.transactionNo AND t.sourceType = u.sourceType WHERE t.fileUploadDate = :today")
    BigDecimal sumTodayUnmatchedAmount(@Param("today") String today);
    void deleteByUploadedFile(UploadedFileEntity uploadedFileEntity);
    List<TransactionEntity> findByUploadedFile(UploadedFileEntity uploadedFileEntity);
    void deleteByUploadedFileAndReconStatus(UploadedFileEntity file, ReconStatus status);
    boolean existsByTransactionNoAndReferenceNoAndAmountAndLegacyIdAndTransactionDateAndSourceType(String transactionNo,String referenceNo,BigDecimal amount,String legacyId,String paidDate,SourceType sourceType);
    List<TransactionEntity> findByReconStatusIn(List<ReconStatus> statuses);
    @Query("SELECT t.transactionNo FROM TransactionEntity t WHERE t.uploadedFile.id = :fileId")
    List<String> findTransactionNosByFileId(@Param("fileId") Integer fileId);
    @Query(value = "SELECT p.transaction_no AS payNo, s.transaction_no AS setNo " +
            "FROM transaction p " +
            "JOIN transaction s ON p.transaction_no = s.transaction_no " +
            "AND p.reference_no = s.reference_no " +
            "AND p.amount = s.amount " +
            "AND p.legacy_id = s.legacy_id " +
            "AND p.transaction_date = s.transaction_date " +
            "AND p.org_country = s.org_country " +
            "WHERE p.source_type = 'PAYMENT' " +
            "AND s.source_type = 'SETTLEMENT' " +
            "AND p.recon_status = 'S' " +
            "AND s.recon_status = 'S'", nativeQuery = true)
    List<Object[]> findAllPotentialMatches();
    // 4. Find all currently Unmatched transactions that aren't in the Unmatched table yet
    @Query("SELECT t FROM TransactionEntity t WHERE t.reconStatus = 'U' AND t.transactionNo NOT IN (SELECT u.transactionNo FROM ReconciliationUnmatched u)")
    List<TransactionEntity> findNewUnmatched();
    // 1. Bulk update status for matched IDs
    @Modifying
    @Query("UPDATE TransactionEntity t SET t.reconStatus = :status WHERE t.transactionNo IN :nos")
    void updateStatusBulk(@Param("nos") List<String> nos, @Param("status") ReconStatus status);

    // 2. Bulk update remaining 'S' (Staged) to 'U' (Unmatched)
    @Modifying
    @Query("UPDATE TransactionEntity t SET t.reconStatus = :newStatus WHERE t.reconStatus = :oldStatus")
    void updateRemainingToUnmatched(@Param("oldStatus") ReconStatus oldStatus, @Param("newStatus") ReconStatus newStatus);
    // We use a Native Query to let the Database do the heavy hashing work
    @Query(value = "SELECT CRC32(CONCAT(transaction_no, reference_no, amount, transaction_date, legacy_id, org_country)) FROM transaction WHERE source_type = :sourceType", nativeQuery = true)
    Set<Long> findAllTransactionHashes(@Param("sourceType") String sourceType);
}
