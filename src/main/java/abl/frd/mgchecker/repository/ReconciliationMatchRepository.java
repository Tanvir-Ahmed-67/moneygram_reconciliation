package abl.frd.mgchecker.repository;

import abl.frd.mgchecker.model.ReconciliationMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ReconciliationMatchRepository extends JpaRepository<ReconciliationMatch, Long> {

    @Query("SELECT CASE WHEN m.paymentFileId = :fileId THEN m.settlementId ELSE m.paymentId END " +
            "FROM ReconciliationMatch m WHERE m.paymentFileId = :fileId OR m.settlementFileId = :fileId")
    List<Integer> findPartnerIdsByFileId(@Param("fileId") Integer fileId);
    @Modifying
    @Query(value = "DELETE FROM reconciliation_match WHERE payment_file_id = :fId OR settlement_file_id = :fId", nativeQuery = true)
    void deleteByFileIdNative(@Param("fId") Integer fId);
}
