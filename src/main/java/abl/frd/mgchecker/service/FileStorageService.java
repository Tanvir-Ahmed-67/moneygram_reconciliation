package abl.frd.mgchecker.service;

import abl.frd.mgchecker.enumpack.FileStatus;
import abl.frd.mgchecker.enumpack.ReconStatus;
import abl.frd.mgchecker.enumpack.SourceType;
import abl.frd.mgchecker.helper.FileSummary;
import abl.frd.mgchecker.model.TransactionEntity;
import abl.frd.mgchecker.model.TransactionStagingEntity;
import abl.frd.mgchecker.model.UploadedFileEntity;
import abl.frd.mgchecker.repository.TransactionRepository;
import abl.frd.mgchecker.repository.TransactionStagingRepository;
import abl.frd.mgchecker.repository.UploadedFileRepository;
import com.github.pjfanning.xlsx.StreamingReader;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class FileStorageService {

    @Autowired
    private TransactionRepository txnRepo;
    @Autowired
    private TransactionStagingRepository txnStagingRepo;
    @Autowired
    private UploadedFileRepository uploadedFileRepository;


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String saveSingleFileAtomic(MultipartFile file, SourceType sourceType) {
        try (InputStream is = file.getInputStream();
             Workbook workbook = StreamingReader.builder()
                     .rowCacheSize(100)    // number of rows to keep in memory (low memory footprint)
                     .bufferSize(4096)     // buffer size used to read from input stream
                     .open(is)) {          // opens the InputStream

            Sheet worksheet = workbook.getSheetAt(0);

            if (!isValidFormat(worksheet, sourceType)) {
                return "Invalid Format: Incorrect source file selected.";
            }
            if (uploadedFileRepository.findByFileNameAndSourceType(file.getOriginalFilename(), sourceType).isPresent()) {
                return "Filename already exists.";
            }

            UploadedFileEntity uploadedFile = new UploadedFileEntity();
            uploadedFile.setFileName(file.getOriginalFilename());
            uploadedFile.setSourceType(sourceType);
            uploadedFile.setUploadTime(LocalDateTime.now());
            uploadedFile.setStatus(FileStatus.STAGED);
            uploadedFile = uploadedFileRepository.save(uploadedFile);

            FileSummary summary = (sourceType == SourceType.PAYMENT)
                    ? parseAndSavePaymentFile(worksheet, sourceType, uploadedFile)
                    : parseAndSaveSettlementFile(worksheet, sourceType, uploadedFile);

            if (summary.getTotalCount() > 0) {
                uploadedFile.setTotalTransactions(summary.getTotalCount());
                uploadedFile.setTotalAmount(summary.getTotalAmount());
                uploadedFile.setTotalAmountUsd(summary.getTotalAmountUsd());
                uploadedFile.setValueDate(summary.getValueDate());
                uploadedFileRepository.save(uploadedFile);
                return "SUCCESS";
            } else {
                // DELETE the file because it is empty
                uploadedFileRepository.delete(uploadedFile);
                // Flushing ensures the delete happens before the transaction commits
                uploadedFileRepository.flush();
                return "Error: The uploaded file contains no valid transactions.";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }

    private FileSummary parseAndSavePaymentFile(Sheet worksheet, SourceType sourceType, UploadedFileEntity uploadedFile) throws Exception {
        List<TransactionStagingEntity> batch = new ArrayList<>();
        String legacyId = "";
        for (Row row : worksheet) {
            int currentIndex = row.getRowNum();

            // Skip rows until we reach start index (Row 6)
            if (currentIndex < 6) {
                continue;
            }
            // Stop if we hit a completely empty row (end of file padding)
            if (isRowEmpty(row)) {
                continue;
            }
            if (row == null) continue;
            String cellB = getCellValueAsString(row.getCell(1)).trim();
            String cellC = getCellValueAsString(row.getCell(2)).trim();
            if (cellB.isEmpty() && cellC.isEmpty()) continue;
            if (cellC.contains("Legacy ID :")) legacyId = getCellValueAsString(row.getCell(6)).trim();
            if (cellB.contains("Account Number :") || cellB.isEmpty() || cellB.contains("Settlement Currency :")) continue;

            String cellValueAmount = getCellValueAsString(row.getCell(25)).trim().replace("-", "");
            BigDecimal amount = (cellValueAmount.isEmpty()) ? BigDecimal.ZERO : new BigDecimal(cellValueAmount);
            if (amount.compareTo(BigDecimal.ZERO) == 0) continue;

            String txnNo = getCellValueAsString(row.getCell(4)).trim();
            String refNo = getCellValueAsString(row.getCell(8)).trim();
            LocalDate paidDate = convertStringToLocalDate(cellB, "MM/dd/yyyy");
            String originatingCountry = getCellValueAsString(row.getCell(14)).trim();

            TransactionStagingEntity txn = new TransactionStagingEntity();
            txn.setTransactionNo(txnNo);
            txn.setReferenceNo(refNo);
            txn.setOriginatingCountry(originatingCountry);
            txn.setAmount(amount);
            txn.getAmountUsd();
            txn.setLegacyId(legacyId);
            txn.setTransactionDate(String.valueOf(paidDate));
            txn.setFileUploadDate(uploadedFile.getUploadTime());
            txn.setSourceType(sourceType);
            txn.setReconStatus(ReconStatus.S);
            txn.setUploadedFile(uploadedFile);
            batch.add(txn);

            if (batch.size() >= 1000) {
                txnStagingRepo.saveAll(batch);
                batch.clear();
            }
        }
        // 4. Final check: ensure the file is managed for the last remaining batch
        if (!batch.isEmpty()) {
            txnStagingRepo.saveAll(batch);
        }
        // THE MAGIC STEP: Move to real table and ignore duplicates
        txnStagingRepo.moveNewRecordsFromStaging(uploadedFile.getId());
        // 2. Fetch the "True" summary of what was actually moved
        Object result = txnStagingRepo.getImportedSummary(uploadedFile.getId());
        Object[] row = (Object[]) result;

        int actualCount = (row[0] != null) ? ((Number) row[0]).intValue() : 0;
        BigDecimal actualAmount = (row[1] != null) ? (BigDecimal) row[1] : BigDecimal.ZERO;
        BigDecimal actualAmountUsd = (row[2] != null) ? (BigDecimal) row[2] : BigDecimal.ZERO;
        // Clean up staging
        txnStagingRepo.clearStaging(uploadedFile.getId());

        return new FileSummary(actualCount, actualAmount, null,actualAmountUsd);
    }
    private FileSummary parseAndSaveSettlementFile(Sheet worksheet, SourceType sourceType, UploadedFileEntity uploadedFile) throws Exception {
        List<TransactionStagingEntity> batch = new ArrayList<>();
        String legacyId = "";
        LocalDate valueDate = null;
        TransactionStagingEntity currentTxn = null; // Temporary holder for the paired rows

        for (Row row : worksheet) {
            int currentIndex = row.getRowNum();

            // 1. Extract Value Date (Row 4)
            if (currentIndex == 4) {
                String input = getCellValueAsString(row.getCell(1));
                String dateString = input.substring(input.indexOf(":") + 1).trim();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
                valueDate = LocalDate.parse(dateString, formatter);
            }

            // Skip headers
            if (currentIndex < 8) continue;
            if (isRowEmpty(row)) continue;

            String cellB = getCellValueAsString(row.getCell(1)).trim();
            String cellC = getCellValueAsString(row.getCell(4)).trim();
            String cellW = getCellValueAsString(row.getCell(22)).trim();

            // 2. Capture Legacy ID
            if (cellC.contains("Legacy ID :")) {
                legacyId = getCellValueAsString(row.getCell(8)).trim();
            }

            // 3. Process TRN (Transaction Row)
            if ("trn".equalsIgnoreCase(cellW)) {
                String cellValueAmount = getCellValueAsString(row.getCell(23)).trim().replace("-", "");
                BigDecimal amount = (cellValueAmount.isEmpty()) ? BigDecimal.ZERO : new BigDecimal(cellValueAmount);
                if (amount.compareTo(BigDecimal.ZERO) == 0) continue;

                String txnNo = getCellValueAsString(row.getCell(3)).trim();
                String refNo = getCellValueAsString(row.getCell(7)).trim();
                LocalDate paidDate = convertStringToLocalDate(cellB, "MM/dd/yyyy");
                String originatingCountry = getCellValueAsString(row.getCell(11)).trim();

                currentTxn = new TransactionStagingEntity();
                currentTxn.setTransactionNo(txnNo);
                currentTxn.setReferenceNo(refNo);
                currentTxn.setOriginatingCountry(originatingCountry);
                currentTxn.setAmount(amount);
                currentTxn.setLegacyId(legacyId);
                currentTxn.setTransactionDate(String.valueOf(paidDate));
                currentTxn.setFileUploadDate(uploadedFile.getUploadTime());
                currentTxn.setSourceType(sourceType);
                currentTxn.setReconStatus(ReconStatus.S);
                currentTxn.setUploadedFile(uploadedFile);

                // We do NOT add to batch yet, waiting for 'stl' row
            }
            // 4. Process STL (Settlement Row)
            else if ("stl".equalsIgnoreCase(cellW) && currentTxn != null) {
                String cellValueAmountUsd = getCellValueAsString(row.getCell(23)).trim().replace("-", "");
                BigDecimal amountUsd = (cellValueAmountUsd.isEmpty()) ? BigDecimal.ZERO : new BigDecimal(cellValueAmountUsd);

                currentTxn.setAmountUsd(amountUsd); // Set the USD value
                batch.add(currentTxn);               // Now the object is complete, add to batch
                currentTxn = null;                   // Reset for the next transaction pair
            }

            // Batch saving logic
            if (batch.size() >= 1000) {
                txnStagingRepo.saveAll(batch);
                batch.clear();
            }
        }

        if (!batch.isEmpty()) {
            txnStagingRepo.saveAll(batch);
        }

        // Existing repository logic...
        txnStagingRepo.moveNewRecordsFromStaging(uploadedFile.getId());
        Object result = txnStagingRepo.getImportedSummary(uploadedFile.getId());
        Object[] resultRow = (Object[]) result;

        int actualCount = (resultRow[0] != null) ? ((Number) resultRow[0]).intValue() : 0;
        BigDecimal actualAmount = (resultRow[1] != null) ? (BigDecimal) resultRow[1] : BigDecimal.ZERO;
        BigDecimal actualAmountUsd = (resultRow[2] != null) ? (BigDecimal) resultRow[2] : BigDecimal.ZERO;

        txnStagingRepo.clearStaging(uploadedFile.getId());

        return new FileSummary(actualCount, actualAmount, valueDate, actualAmountUsd);
    }


/*
    private FileSummary parseAndSaveSettlementFile(Sheet worksheet, SourceType sourceType, UploadedFileEntity uploadedFile) throws Exception {
        List<TransactionStagingEntity> batch = new ArrayList<>();
        String legacyId = "";
        LocalDate valueDate= null;
        for (Row row : worksheet) {
            int currentIndex = row.getRowNum();
            if(currentIndex == 4){
                String input = getCellValueAsString(row.getCell(1));
                String dateString = input.substring(input.indexOf(":") + 1).trim();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
                valueDate = LocalDate.parse(dateString, formatter);
            }
            // Skip rows until we reach your start index (Row 6)
            if (currentIndex < 8) {
                continue;
            }
            // 2. Stop if we hit a completely empty row (end of file padding)
            if (isRowEmpty(row)) {
                continue;
            }
            if (row == null) continue;
            String cellB = getCellValueAsString(row.getCell(1)).trim();
            String cellC = getCellValueAsString(row.getCell(4)).trim();
            String cellW = getCellValueAsString(row.getCell(22)).trim();
            if (cellC.contains("Legacy ID :")) legacyId = getCellValueAsString(row.getCell(8)).trim();
            if (cellB.isEmpty() && cellC.isEmpty()) continue;

            if (cellW.equals("trn")) {
                String cellValueAmount = getCellValueAsString(row.getCell(23)).trim().replace("-", "");
                BigDecimal amount = (cellValueAmount.isEmpty()) ? BigDecimal.ZERO : new BigDecimal(cellValueAmount);
                if (amount.compareTo(BigDecimal.ZERO) == 0) continue;

                String txnNo = getCellValueAsString(row.getCell(3)).trim();
                String refNo = getCellValueAsString(row.getCell(7)).trim();
                LocalDate paidDate = convertStringToLocalDate(cellB, "MM/dd/yyyy");
                String originatingCountry = getCellValueAsString(row.getCell(11)).trim();

                TransactionStagingEntity txn = new TransactionStagingEntity();
                txn.setTransactionNo(txnNo);
                txn.setReferenceNo(refNo);
                txn.setOriginatingCountry(originatingCountry);
                txn.setAmount(amount);
                txn.setLegacyId(legacyId);
                txn.setTransactionDate(String.valueOf(paidDate));
                txn.setFileUploadDate(uploadedFile.getUploadTime());
                txn.setSourceType(sourceType);
                txn.setReconStatus(ReconStatus.S);
                txn.setUploadedFile(uploadedFile);
                batch.add(txn);
            }
            if (batch.size() >= 1000) {
                txnStagingRepo.saveAll(batch);
                batch.clear();
            }
        }
        // 4. Final check: ensure the file is managed for the last remaining batch
        if (!batch.isEmpty()) {
            txnStagingRepo.saveAll(batch);
        }
        // THE MAGIC STEP: Move to real table and ignore duplicates
        txnStagingRepo.moveNewRecordsFromStaging(uploadedFile.getId());
        // 2. Fetch the "True" summary of what was actually moved
        Object result = txnStagingRepo.getImportedSummary(uploadedFile.getId());
        Object[] row = (Object[]) result;

        int actualCount = (row[0] != null) ? ((Number) row[0]).intValue() : 0;
        BigDecimal actualAmount = (row[1] != null) ? (BigDecimal) row[1] : BigDecimal.ZERO;
        BigDecimal actualAmountUsd = (row[2] != null) ? (BigDecimal) row[2] : BigDecimal.ZERO;

        // Clean up staging
        txnStagingRepo.clearStaging(uploadedFile.getId());

        return new FileSummary(actualCount, actualAmount, valueDate, actualAmountUsd);
    }

 */
private boolean isValidFormat(Sheet sheet, SourceType sourceType) {
    int targetRow = 3;
    int current = 0;
    for (Row row : sheet) {
        if (current == targetRow) {
            String probe = getCellValueAsString(row.getCell(1));
            return (sourceType == SourceType.PAYMENT)
                    ? probe.contains("Settlement Currency")
                    : probe.contains("Settlement Id");
        }
        current++;
        if (current > targetRow) break;
    }
    return false;
}


    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue().trim();
            case NUMERIC: return DateUtil.isCellDateFormatted(cell) ? cell.getDateCellValue().toString() : BigDecimal.valueOf(cell.getNumericCellValue()).toPlainString();
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            default: return "";
        }
    }

    private LocalDate convertStringToLocalDate(String date, String format) {
        try { return LocalDate.parse(date, DateTimeFormatter.ofPattern(format)); }
        catch (Exception e) { return null; }
    }
    private boolean isRowEmpty(Row row) {
        if (row == null) return true;
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            // If we find even one cell with data, the row is NOT empty
            if (cell != null && cell.getCellType() != CellType.BLANK && !getCellValueAsString(cell).trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }
}