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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReconciliationService {
    @Autowired
    @Lazy // Prevents circular dependency error
    private ReconciliationService self;
    @PersistenceContext
    private EntityManager entityManager;

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
    public void reconcileIncremental() {
        // PHASE 1: EXACT MATCHING
        List<Object[]> matches = txnRepo.findAllPotentialMatches();
        List<ReconciliationMatch> matchBatch = new ArrayList<>();
        List<Integer> matchedIds = new ArrayList<>();

        for (Object[] row : matches) {
            ReconciliationMatch m = new ReconciliationMatch();
            m.setPaymentId((Integer) row[0]);
            m.setSettlementId((Integer) row[1]);
            m.setTransactionNo((String) row[2]);
            m.setAmount((BigDecimal) row[3]);
            m.setReferenceNo((String) row[4]);
            m.setOrgCountry((String) row[5]);
            m.setTransactionDate((String) row[6]);
            m.setLegacyId((String) row[7]);
            m.setPaymentFileId((Integer) row[8]);
            m.setSettlementFileId((Integer) row[9]);
            m.setMatchedOn(LocalDateTime.now());
            m.setMatchType(MatchType.EXACT);

            matchBatch.add(m);
            matchedIds.add((Integer) row[0]);
            matchedIds.add((Integer) row[1]);

            if (matchBatch.size() >= 500) {
                self.saveMatchesAndCleanUnmatched(matchBatch, matchedIds);
                matchBatch.clear();
                matchedIds.clear();
            }
        }
        if (!matchBatch.isEmpty()) {
            self.saveMatchesAndCleanUnmatched(matchBatch, matchedIds);
        }

        // PHASE 2: PHYSICAL COMMIT OF 'U' STATUS
        self.markRemainingAsUnmatched();

        // PHASE 3: SYNC TO REPORT TABLE
        self.syncUnmatchedTable();

        // PHASE 4: UPDATE FILES
        self.updateFileStatuses();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveMatchesAndCleanUnmatched(List<ReconciliationMatch> matches, List<Integer> ids) {
        matchRepo.saveAll(matches);
        // Use the NATIVE method
        txnRepo.updateStatusByIdsNative(ids, "M");
        unmatchRepo.deleteByTransactionIdInNative(ids);

        // Clear Hibernate memory so it doesn't revert 'M' back to 'S'
        entityManager.flush();
        entityManager.clear();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRemainingAsUnmatched() {
        // Force the update at the DB level
        txnRepo.updateRemainingToUnmatchedNative("S", "U");
        entityManager.flush();
        entityManager.clear();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncUnmatchedTable() {
        // Use the NATIVE method to see the 'U' status physically in the DB
        List<TransactionEntity> newUnmatched = txnRepo.findNewUnmatchedNative();
        List<ReconciliationUnmatched> batch = new ArrayList<>();

        for (TransactionEntity txn : newUnmatched) {
            ReconciliationUnmatched um = new ReconciliationUnmatched();
            um.setTransactionId(txn.getId());
            um.setTransactionNo(txn.getTransactionNo());
            um.setReferenceNo(txn.getReferenceNo());
            um.setSourceType(txn.getSourceType());
            um.setReason(UnmatchReason.NOT_FOUND);
            um.setDetectedOn(LocalDateTime.now());
            if(txn.getUploadedFile() != null) {
                um.setFileId(txn.getUploadedFile().getId());
            }
            batch.add(um);

            if (batch.size() >= 500) {
                unmatchRepo.saveAll(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) unmatchRepo.saveAll(batch);

        entityManager.flush();
        entityManager.clear();
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
