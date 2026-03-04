package abl.frd.mgchecker.repository;

import abl.frd.mgchecker.model.ReconciliationMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ReconciliationMatchRepository extends JpaRepository<ReconciliationMatch, Long> {
    @Modifying
    @Transactional
    @Query("DELETE FROM ReconciliationMatch m WHERE m.paymentTransactionNo IN :txnNos OR m.settlementTransactionNo IN :txnNos")
    void deleteByPaymentTxnNoInOrSettlementTxnNoIn(@Param("txnNos") List<String> txnNos);
    boolean existsByPaymentTransactionNoAndSettlementTransactionNo(String payId, String setId);
}
