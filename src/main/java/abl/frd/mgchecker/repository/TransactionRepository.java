package abl.frd.mgchecker.repository;

import abl.frd.mgchecker.enumpack.ReconStatus;
import abl.frd.mgchecker.model.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Set;

public interface TransactionRepository extends JpaRepository<TransactionEntity, Integer> {
    List<TransactionEntity> findByReconStatus(ReconStatus status);
    @Modifying
    @Transactional
    @Query(value = "UPDATE transaction SET recon_status = 'S' WHERE recon_status IN ('M', 'U')", nativeQuery = true)
    void resetAllToStaged();
    @Query(value = "SELECT " +
            "p.id AS payId, " +
            "s.id AS setId, " +
            "p.transaction_no, " +
            "p.amount, " +
            "p.reference_no, " +
            "p.org_country, " +
            "p.transaction_date, " +
            "p.legacy_id, " +
            "p.file_id AS payFileId, " +
            "s.file_id AS setFileId, " +
            "p.source_type " +
            "FROM transaction p " +
            "JOIN transaction s ON p.transaction_no = s.transaction_no " +
            "AND p.reference_no = s.reference_no " +
            "AND p.amount = s.amount " +
            "AND p.transaction_date = s.transaction_date " +
            "AND p.legacy_id = s.legacy_id " +
            "AND p.org_country = s.org_country " +
            "WHERE p.source_type = 'PAYMENT' AND s.source_type = 'SETTLEMENT' " +
            "AND p.recon_status IN ('S', 'U') AND s.recon_status IN ('S', 'U')", nativeQuery = true)
    List<Object[]> findAllPotentialMatches();
    // 2. Bulk update remaining 'S' (Staged) to 'U' (Unmatched)
    @Query(value = "SELECT CRC32(CONCAT(transaction_no, reference_no, amount, transaction_date, legacy_id, org_country)) FROM transaction WHERE source_type = :sourceType", nativeQuery = true)
    Set<Long> findAllTransactionHashes(@Param("sourceType") String sourceType);
    @Modifying
    @Transactional
    @Query(value = "UPDATE transaction SET recon_status = :newStatus WHERE recon_status = :oldStatus", nativeQuery = true)
    void updateRemainingToUnmatchedNative(@Param("oldStatus") String oldStatus, @Param("newStatus") String newStatus);
    @Modifying
    @Transactional
    @Query(value = "UPDATE transaction SET recon_status = :status WHERE id IN :ids", nativeQuery = true)
    void updateStatusByIdsNative(@Param("ids") List<Integer> ids, @Param("status") String status);
    @Query(value = "SELECT * FROM transaction WHERE recon_status = 'U' " +
            "AND id NOT IN (SELECT transaction_id FROM reconciliation_unmatched)", nativeQuery = true)
    List<TransactionEntity> findNewUnmatchedNative();
    @Modifying
    @Query(value = "DELETE FROM transaction WHERE file_id = :fId", nativeQuery = true)
    void deleteByFileIdNative(@Param("fId") Integer fId);
    
    @Query(value = "SELECT file_id FROM transaction WHERE transaction_date = :txnDate AND source_type = 'SETTLEMENT' LIMIT 1", nativeQuery = true)
    Integer findSettlementFileIdByDate(@Param("txnDate") String txnDate);
}
