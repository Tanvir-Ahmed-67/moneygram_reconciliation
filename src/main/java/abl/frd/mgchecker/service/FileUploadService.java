package abl.frd.mgchecker.service;

import abl.frd.mgchecker.enumpack.ReconStatus;
import abl.frd.mgchecker.enumpack.SourceType;
import abl.frd.mgchecker.model.TransactionEntity;
import abl.frd.mgchecker.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class FileUploadService {

    private final TransactionRepository txnRepo;
    private final ReconciliationService reconciliationService;

    public FileUploadService(TransactionRepository txnRepo,
                             ReconciliationService reconciliationService) {
        this.txnRepo = txnRepo;
        this.reconciliationService = reconciliationService;
    }

    @Transactional
    public void processFiles(MultipartFile paymentFile, MultipartFile settlementFile) throws Exception {

        // 1️⃣ Parse Payment file
        parseAndSave(paymentFile, SourceType.PAYMENT);

        // 2️⃣ Parse Settlement file
        parseAndSave(settlementFile, SourceType.SETTLEMENT);

        // 3️⃣ Run reconciliation including unmatched re-check
        reconciliationService.reconcileIncremental();
    }

    private void parseAndSave(MultipartFile file, SourceType sourceType) throws Exception {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            List<TransactionEntity> batch = new ArrayList<>();
            while ((line = br.readLine()) != null) {
                String[] cols = line.split(";");

                // Skip header if present
                if (cols[0].equalsIgnoreCase("transactionNo")) continue;

                TransactionEntity txn = new TransactionEntity();
                txn.setTransactionNo(cols[0].trim());
                txn.setCurrency(cols[1].trim());
                txn.setAmount(new Double(cols[2].trim()));
                txn.setTransactionDate(String.valueOf(LocalDate.parse(cols[3].trim())));
                txn.setFileUploadDate(String.valueOf(LocalDateTime.now()));
                txn.setSourceType(sourceType);
                txn.setReconStatus(ReconStatus.N);

                batch.add(txn);

                if (batch.size() >= 500) {
                    txnRepo.saveAll(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                txnRepo.saveAll(batch);
            }
        }
    }
    @Transactional
    public void reconcile() {

        // 1. fetch unprocessed PAYMENT txns
        // 2. join with SETTLEMENT txns (native query / JPQL)
        // 3. insert matches
        // 4. update reconStatus
        // 5. insert unmatched references
    }

}
