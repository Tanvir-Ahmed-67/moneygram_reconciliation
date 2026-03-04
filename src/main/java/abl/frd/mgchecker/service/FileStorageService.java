package abl.frd.mgchecker.service;

import abl.frd.mgchecker.enumpack.FileStatus;
import abl.frd.mgchecker.enumpack.ReconStatus;
import abl.frd.mgchecker.enumpack.SourceType;
import abl.frd.mgchecker.helper.FileSummary;
import abl.frd.mgchecker.model.TransactionEntity;
import abl.frd.mgchecker.model.UploadedFileEntity;
import abl.frd.mgchecker.repository.TransactionRepository;
import abl.frd.mgchecker.repository.UploadedFileRepository;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
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
    private UploadedFileRepository uploadedFileRepository;

    private long generateHash(String txnNo, String refNo, BigDecimal amount, String date, String legacyId, String orgCountry) {
        String fingerprint = txnNo + "|" + refNo + "|" + amount.stripTrailingZeros().toPlainString() + "|" + date + "|" + legacyId + "|" + orgCountry;
        // Uses Guava to create a 64-bit hash
        return com.google.common.hash.Hashing.murmur3_128().hashString(fingerprint, StandardCharsets.UTF_8).asLong();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String saveSingleFileAtomic(MultipartFile file, SourceType sourceType) {
        try {
            Workbook workbook = WorkbookFactory.create(file.getInputStream());
            Sheet worksheet = workbook.getSheetAt(0);

            if (!isValidFormat(worksheet, sourceType)) {
                return "Invalid Format: Incorrect source file selected.";
            }
            if (uploadedFileRepository.findByFileNameAndSourceType(file.getOriginalFilename(), sourceType).isPresent()) {
                return "Filename already exists.";
            }
            // Fetching 1M Longs takes ~8MB of RAM. (Compare to ~150MB+ for Strings)
            Set<Long> existingHashes = txnRepo.findAllTransactionHashes(sourceType.name());

            UploadedFileEntity uploadedFile = new UploadedFileEntity();
            uploadedFile.setFileName(file.getOriginalFilename());
            uploadedFile.setSourceType(sourceType);
            uploadedFile.setUploadTime(LocalDateTime.now());
            uploadedFile.setStatus(FileStatus.STAGED);
            uploadedFile = uploadedFileRepository.save(uploadedFile);

            FileSummary summary = (sourceType == SourceType.PAYMENT)
                    ? parseAndSavePaymentFile(worksheet, sourceType, uploadedFile, existingHashes)
                    : parseAndSaveSettlementFile(worksheet, sourceType, uploadedFile, existingHashes);

            uploadedFile.setTotalTransactions(summary.getTotalCount());
            uploadedFile.setTotalAmount(summary.getTotalAmount());
            uploadedFileRepository.save(uploadedFile);
            return "SUCCESS";
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }

    private FileSummary parseAndSavePaymentFile(Sheet worksheet, SourceType sourceType, UploadedFileEntity uploadedFile, Set<Long> existingHashes) throws Exception {
        List<TransactionEntity> batch = new ArrayList<>();
        String legacyId = "";
        int totalCount = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (int rowIndex = 6; rowIndex <= worksheet.getLastRowNum(); rowIndex++) {
            Row row = worksheet.getRow(rowIndex);
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

            // 2. Generate Hash and Check RAM
            long rowHash = generateHash(txnNo, refNo, amount, cellB,legacyId,originatingCountry);

            if (existingHashes.contains(rowHash)) {
                continue; // SKIP: Already in DB or already seen in this file
            }
            // 3. Add to "Seen" and Batch
            existingHashes.add(rowHash);

            TransactionEntity txn = new TransactionEntity();
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

            totalCount++;
            totalAmount = totalAmount.add(amount);
            if (batch.size() >= 500) {
                txnRepo.saveAll(batch);
                batch.clear();
            }
        }
        // 4. Final check: ensure the file is managed for the last remaining batch
        if (!batch.isEmpty()) {
            txnRepo.saveAll(batch);
        }
        return new FileSummary(totalCount, totalAmount);
    }

    private FileSummary parseAndSaveSettlementFile(Sheet worksheet, SourceType sourceType, UploadedFileEntity uploadedFile, Set<Long> existingHashes) throws Exception {
        List<TransactionEntity> batch = new ArrayList<>();
        String legacyId = "";
        int totalCount = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (int rowIndex = 8; rowIndex <= worksheet.getLastRowNum(); rowIndex++) {
            Row row = worksheet.getRow(rowIndex);
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

                // 2. Generate Hash and Check RAM
                long rowHash = generateHash(txnNo, refNo, amount, cellB,legacyId,originatingCountry);
                if (existingHashes.contains(rowHash)) {
                    continue; // SKIP: Already in DB or already seen in this file
                }
                // 3. Add to "Seen" and Batch
                existingHashes.add(rowHash);

                TransactionEntity txn = new TransactionEntity();
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
                totalCount++;
                totalAmount = totalAmount.add(amount);
            }
            if (batch.size() >= 500) {
                txnRepo.saveAll(batch);
                batch.clear();
            }
        }
        // 4. Final check: ensure the file is managed for the last remaining batch
        if (!batch.isEmpty()) {
            txnRepo.saveAll(batch);
        }
        return new FileSummary(totalCount, totalAmount);
    }

    private boolean isValidFormat(Sheet sheet, SourceType sourceType) {
        try {
            String probe = getCellValueAsString(sheet.getRow(3).getCell(1));
            return (sourceType == SourceType.PAYMENT) ? probe.contains("Settlement Currency") : probe.contains("Settlement Id");
        } catch (Exception e) { return false; }
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
}