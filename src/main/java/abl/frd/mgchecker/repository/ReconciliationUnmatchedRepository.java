package abl.frd.mgchecker.repository;

import abl.frd.mgchecker.model.ReconciliationUnmatched;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ReconciliationUnmatchedRepository extends JpaRepository<ReconciliationUnmatched, Long> {
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM reconciliation_unmatched WHERE transaction_id IN :ids", nativeQuery = true)
    void deleteByTransactionIdInNative(@Param("ids") List<Integer> ids);
    @Modifying
    @Query(value = "DELETE FROM reconciliation_unmatched WHERE file_id = :fId", nativeQuery = true)
    void deleteByFileIdNative(@Param("fId") Integer fId);
}
