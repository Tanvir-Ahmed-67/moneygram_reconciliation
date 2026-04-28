package abl.frd.mgchecker.repository;

import abl.frd.mgchecker.model.TransactionEntity;
import abl.frd.mgchecker.model.TransactionStagingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface TransactionStagingRepository extends JpaRepository<TransactionStagingEntity, Integer> {
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM transaction_staging_entity WHERE file_id = :fileId", nativeQuery = true)
    void clearStaging(@Param("fileId") int fileId);
    @Modifying
    @Transactional
    @Query(value = "INSERT IGNORE INTO transaction " +
            "(transaction_no, reference_no, org_country, amount,amount_usd, transaction_date, " +
            "file_upload_date, legacy_id, source_type, recon_status, file_id) " +
            "SELECT transaction_no, reference_no, org_country, amount, amount_usd, transaction_date, " +
            "file_upload_date, legacy_id, source_type, recon_status, file_id " +
            "FROM transaction_staging_entity WHERE file_id = :fileId", nativeQuery = true)
    void moveNewRecordsFromStaging(@Param("fileId") int fileId);
    @Query(value = "SELECT COUNT(*), SUM(s.amount), SUM(s.amount_usd) FROM transaction_staging_entity s " +
            "JOIN transaction t ON s.transaction_no = t.transaction_no " +
            "AND s.reference_no = t.reference_no " +
            "AND s.amount = t.amount " +
            "AND s.transaction_date = t.transaction_date " +
            "AND s.legacy_id = t.legacy_id " +
            "AND s.org_country = t.org_country " +
            "AND s.source_type = t.source_type " +
            "WHERE s.file_id = :fileId AND t.file_id = :fileId", nativeQuery = true)
    Object getImportedSummary(@Param("fileId") int fileId);
}
