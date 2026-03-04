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
import java.util.ArrayList;
import java.util.List;

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
        // Step 1: Bulk Find Matches
        List<Object[]> matches = txnRepo.findAllPotentialMatches();
        List<ReconciliationMatch> matchBatch = new ArrayList<>();
        List<String> matchedNos = new ArrayList<>();

        for (Object[] row : matches) {
            ReconciliationMatch m = new ReconciliationMatch();
            m.setPaymentTransactionNo((String) row[0]);
            m.setSettlementTransactionNo((String) row[1]);
            m.setMatchType(MatchType.EXACT);
            m.setMatchedOn(LocalDateTime.now());

            matchBatch.add(m);
            matchedNos.add((String) row[0]);
            matchedNos.add((String) row[1]);

            if (matchBatch.size() >= 500) {
                matchRepo.saveAll(matchBatch);
                txnRepo.updateStatusBulk(matchedNos, ReconStatus.M);
                matchBatch.clear();
                matchedNos.clear();
            }
        }
        if (!matchBatch.isEmpty()) {
            matchRepo.saveAll(matchBatch);
            txnRepo.updateStatusBulk(matchedNos, ReconStatus.M);
        }
        // Step 2: Mark everything else that was 'S' as 'U' (ONE SQL COMMAND)
        txnRepo.updateRemainingToUnmatched(ReconStatus.S, ReconStatus.U);
        // Step 3: Populate the Unmatched table
        syncUnmatchedTable();
        // STEP 4: UPDATE FILE METADATA STATUS
        updateFileStatuses();
    }
    @Transactional
    public void syncUnmatchedTable() {
        // Get all transactions marked 'U' that aren't in the Unmatched table yet
        List<TransactionEntity> newUnmatched = txnRepo.findNewUnmatched();

        List<ReconciliationUnmatched> batch = new ArrayList<>();
        for (TransactionEntity txn : newUnmatched) {
            ReconciliationUnmatched um = new ReconciliationUnmatched();
            um.setTransactionNo(txn.getTransactionNo());
            um.setReferenceNo(txn.getReferenceNo());
            um.setSourceType(txn.getSourceType());
            um.setReason(UnmatchReason.NOT_FOUND);
            um.setDetectedOn(LocalDateTime.now());
            batch.add(um);

            if (batch.size() >= 500) {
                unmatchRepo.saveAll(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            unmatchRepo.saveAll(batch);
        }
    }
    @Transactional
    public void reconcileFromScratch() {
        matchRepo.deleteAll();
        unmatchRepo.deleteAll();
        txnRepo.resetAllToStaged();
        // 4. Run the standard logic
        reconcileIncremental();
    }
    @Transactional
    public void updateFileStatuses() {
        // 1. Identify files that have finished reconciliation
        List<UploadedFileEntity> readyFiles = uploadedFileRepo.findFilesReadyToProcess();

        if (!readyFiles.isEmpty()) {
            for (UploadedFileEntity file : readyFiles) {
                file.setStatus(FileStatus.PROCESSED);
            }
            // 2. Save all status changes in one batch
            uploadedFileRepo.saveAll(readyFiles);
        }
    }
}
