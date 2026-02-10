package abl.frd.mgchecker.service;

import abl.frd.mgchecker.enumpack.MatchType;
import abl.frd.mgchecker.enumpack.ReconStatus;
import abl.frd.mgchecker.enumpack.SourceType;
import abl.frd.mgchecker.enumpack.UnmatchReason;
import abl.frd.mgchecker.model.ReconciliationMatch;
import abl.frd.mgchecker.model.ReconciliationUnmatched;
import abl.frd.mgchecker.model.TransactionEntity;
import abl.frd.mgchecker.repository.ReconciliationMatchRepository;
import abl.frd.mgchecker.repository.ReconciliationUnmatchedRepository;
import abl.frd.mgchecker.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ReconciliationService {

    private final TransactionRepository txnRepo;
    private final ReconciliationMatchRepository matchRepo;
    private final ReconciliationUnmatchedRepository unmatchRepo;

    public ReconciliationService(TransactionRepository txnRepo,
                                 ReconciliationMatchRepository matchRepo,
                                 ReconciliationUnmatchedRepository unmatchRepo) {
        this.txnRepo = txnRepo;
        this.matchRepo = matchRepo;
        this.unmatchRepo = unmatchRepo;
    }

    @Transactional
    public void reconcileIncremental() {

        // 1️⃣ Exact matches between new unprocessed PAYMENT and SETTLEMENT
        List<Object[]> matches = txnRepo.findExactMatches(SourceType.PAYMENT, SourceType.SETTLEMENT, ReconStatus.N);

        for (Object[] row : matches) {
            String payId = String.valueOf(row[0]);
            String setId = String.valueOf(row[1]);
            saveMatchAndUpdateStatus(payId, setId);
        }

        // 2️⃣ Re-check previous unmatched transactions
        List<ReconciliationUnmatched> unmatchedList = unmatchRepo.findAll();
        for (ReconciliationUnmatched um : unmatchedList) {

            //TransactionEntity txn = txnRepo.findByTransactionNo(um.getTransactionNo());
            TransactionEntity txn = txnRepo.findByTransactionNoAndSourceType(um.getTransactionNo(), um.getSourceType()).orElse(null);

            if (txn == null) continue;

            List<TransactionEntity> possibleMatches =
                    txnRepo.findByTransactionNoAndAmountAndTransactionDateAndReconStatus(
                                    txn.getTransactionNo(),
                                    txn.getAmount(),
                                    txn.getTransactionDate(),
                                    ReconStatus.N
                            ).stream()
                            .filter(t -> t.getSourceType() != txn.getSourceType())
                            .collect(Collectors.toList());

            if (!possibleMatches.isEmpty()) {
                TransactionEntity matchedTxn = possibleMatches.get(0);
                saveMatchAndUpdateStatus(
                        (txn.getSourceType() == SourceType.PAYMENT) ? txn.getTransactionNo() : matchedTxn.getTransactionNo(),
                        (txn.getSourceType() == SourceType.SETTLEMENT) ? txn.getTransactionNo() : matchedTxn.getTransactionNo()
                );
                unmatchRepo.delete(um);
            }

        }

        // 3️⃣ Add remaining unprocessed transactions to unmatched
        List<TransactionEntity> stillUnmatched = txnRepo.findByReconStatus(ReconStatus.N);
        for (TransactionEntity txn : stillUnmatched) {
            boolean alreadyExists = unmatchRepo.existsByTransactionNoAndSourceType(
                    txn.getTransactionNo(),
                    txn.getSourceType()
            );

            if (alreadyExists) {
                continue; // do not insert duplicate
            }
            ReconciliationUnmatched um = new ReconciliationUnmatched();
            um.setTransactionNo(txn.getTransactionNo());
            um.setSourceType(txn.getSourceType());
            um.setReason(UnmatchReason.NOT_FOUND);
            um.setDetectedOn(LocalDateTime.now());

            unmatchRepo.save(um);
            txn.setReconStatus(ReconStatus.U);
        }
    }

    private void saveMatchAndUpdateStatus(String paymentId, String settlementId) {
        ReconciliationMatch match = new ReconciliationMatch();
        match.setPaymentTransactionNo(paymentId);
        match.setSettlementTransactionNo(settlementId);
        match.setMatchType(MatchType.EXACT);
        match.setMatchedOn(LocalDateTime.now());

        matchRepo.save(match);
        txnRepo.updateStatus(paymentId, ReconStatus.M);
        txnRepo.updateStatus(settlementId, ReconStatus.M);
    }
}
