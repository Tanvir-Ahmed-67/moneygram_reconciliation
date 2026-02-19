package abl.frd.mgchecker.service;

import abl.frd.mgchecker.enumpack.*;
import abl.frd.mgchecker.model.ReconciliationMatch;
import abl.frd.mgchecker.model.ReconciliationUnmatched;
import abl.frd.mgchecker.model.TransactionEntity;
import abl.frd.mgchecker.model.UploadedFileEntity;
import abl.frd.mgchecker.repository.ReconciliationMatchRepository;
import abl.frd.mgchecker.repository.ReconciliationUnmatchedRepository;
import abl.frd.mgchecker.repository.TransactionRepository;
import abl.frd.mgchecker.repository.UploadedFileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReconciliationService {

    private final TransactionRepository txnRepo;
    private final ReconciliationMatchRepository matchRepo;
    private final ReconciliationUnmatchedRepository unmatchRepo;
    private final UploadedFileRepository uploadedFileRepo;

    public ReconciliationService(TransactionRepository txnRepo,
                                 ReconciliationMatchRepository matchRepo,
                                 ReconciliationUnmatchedRepository unmatchRepo,
                                 UploadedFileRepository uploadedFileRepo) {
        this.txnRepo = txnRepo;
        this.matchRepo = matchRepo;
        this.unmatchRepo = unmatchRepo;
        this.uploadedFileRepo = uploadedFileRepo;
    }
    @Transactional
    public void reconcileIncremental() {

        // 1️⃣ Exact matches between all staged PAYMENT and SETTLEMENT transactions
        List<Object[]> matches =
                txnRepo.findExactMatches(
                        SourceType.PAYMENT,
                        SourceType.SETTLEMENT,
                        ReconStatus.S
                );
        for (Object[] row : matches) {
            String payId = String.valueOf(row[0]);
            String setId = String.valueOf(row[1]);
            saveMatchAndUpdateStatus(payId, setId);
        }

        // 2️⃣ Re-check all previously unmatched transactions
        List<ReconciliationUnmatched> unmatchedList = unmatchRepo.findAll();

        for (ReconciliationUnmatched um : unmatchedList) {
            TransactionEntity txn = txnRepo
                    .findByTransactionNoAndSourceType(um.getTransactionNo(), um.getSourceType())
                    .orElse(null);

            if (txn == null) continue;

            List<TransactionEntity> possibleMatches = txnRepo
                    .findByTransactionNoAndReferenceNoAndOriginatingCountryAndAmountAndTransactionDateAndReconStatus(
                            txn.getTransactionNo(),
                            txn.getReferenceNo(),
                            txn.getOriginatingCountry(),
                            txn.getAmount(),
                            txn.getTransactionDate(),
                            ReconStatus.S
                    ).stream()
                    .filter(t -> t.getSourceType() != txn.getSourceType())
                    .collect(Collectors.toList());

            if (!possibleMatches.isEmpty()) {
                TransactionEntity matchedTxn = possibleMatches.get(0);
                saveMatchAndUpdateStatus(
                        txn.getSourceType() == SourceType.PAYMENT ? txn.getTransactionNo() : matchedTxn.getTransactionNo(),
                        txn.getSourceType() == SourceType.SETTLEMENT ? txn.getTransactionNo() : matchedTxn.getTransactionNo()
                );
                unmatchRepo.delete(um);
            }
        }
        // 3️⃣ Add remaining staged transactions as unmatched
        List<TransactionEntity> stillUnmatched = txnRepo.findByReconStatus(ReconStatus.S);

        for (TransactionEntity txn : stillUnmatched) {

            boolean alreadyExists = unmatchRepo.existsByTransactionNoAndSourceType(
                    txn.getTransactionNo(),
                    txn.getSourceType()
            );

            if (!alreadyExists) {
                ReconciliationUnmatched um = new ReconciliationUnmatched();
                um.setTransactionNo(txn.getTransactionNo());
                um.setReferenceNo(txn.getReferenceNo());
                um.setSourceType(txn.getSourceType());
                um.setReason(UnmatchReason.NOT_FOUND);
                um.setDetectedOn(LocalDateTime.now());

                unmatchRepo.save(um);
            }
            txn.setReconStatus(ReconStatus.U); // mark as unmatched
        }
        // 4️⃣ Mark all staged files as PROCESSED
        List<UploadedFileEntity> stagedFiles = uploadedFileRepo.findByStatus(FileStatus.STAGED);
        stagedFiles.forEach(file -> file.setStatus(FileStatus.PROCESSED));
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
