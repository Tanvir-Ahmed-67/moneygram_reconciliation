package abl.frd.mgchecker.service;

import abl.frd.mgchecker.enumpack.FileStatus;
import abl.frd.mgchecker.enumpack.ReconStatus;
import abl.frd.mgchecker.enumpack.SourceType;
import abl.frd.mgchecker.helper.FileSummary;
import abl.frd.mgchecker.model.ReconciliationUnmatched;
import abl.frd.mgchecker.model.TransactionEntity;
import abl.frd.mgchecker.model.UploadedFileEntity;
import abl.frd.mgchecker.repository.TransactionRepository;
import abl.frd.mgchecker.repository.UploadedFileRepository;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class FileUploadService {

    private final TransactionRepository txnRepo;
    private final UploadedFileRepository uploadedFileRepository;
    private final ReconciliationService reconciliationService;

    public FileUploadService(TransactionRepository txnRepo, ReconciliationService reconciliationService, UploadedFileRepository uploadedFileRepository) {
        this.txnRepo = txnRepo;
        this.reconciliationService = reconciliationService;
        this.uploadedFileRepository = uploadedFileRepository;
    }

    @Transactional
    public void processFiles(List<MultipartFile> paymentFiles, List<MultipartFile> settlementFiles) throws Exception {
        // 1️⃣ Process Payment Files
        if (paymentFiles != null) {
            for (MultipartFile file : paymentFiles) {
                if (file.isEmpty()) continue;
                saveSinglePaymentFile(file, SourceType.PAYMENT);
            }
        }

        // 2️⃣ Process Settlement Files
        if (settlementFiles != null) {
            for (MultipartFile file : settlementFiles) {
                if (file.isEmpty()) continue;
                saveSingleSettlementFile(file, SourceType.SETTLEMENT);
            }
        }
    }

    private FileSummary parseAndSavePaymentFile(Sheet worksheet, SourceType sourceType, UploadedFileEntity uploadedFile) throws Exception {
        Row row;
        List<TransactionEntity> batch = new ArrayList<>();
        String legacyId= "";
        BigDecimal amount;
        int totalCount = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (int rowIndex = 6; rowIndex <= worksheet.getLastRowNum(); rowIndex++){
            row = worksheet.getRow(rowIndex);
            if(row == null) continue;
            String cellB = getCellValueAsString(row.getCell(1)).trim();
            String cellC = getCellValueAsString(row.getCell(2)).trim();
            if(cellB.trim().isEmpty() && cellC.trim().isEmpty()) continue;
            if(cellC.contains("Legacy ID :")) legacyId = getCellValueAsString(row.getCell(6)).trim();
            if(cellB.contains("Account Number :") || cellB.isEmpty() || cellB.contains("Settlement Currency :")) continue;
            String cellValueAmount = getCellValueAsString(row.getCell(25)).trim().replace("-", "");
            amount = (cellValueAmount.isEmpty()) ? BigDecimal.ZERO : new BigDecimal(cellValueAmount);
            if(amount.compareTo(BigDecimal.ZERO) == 0) continue;
            String transactionNo = getCellValueAsString(row.getCell(4)).trim();
            String referenceNo = getCellValueAsString(row.getCell(8)).trim();
            String originatingCountry = getCellValueAsString(row.getCell(14)).trim();
            LocalDate paidDate = convertStringToLocalDate(cellB, "MM/dd/yyyy");
            TransactionEntity txn = new TransactionEntity();
            txn.setTransactionNo(transactionNo);
            txn.setReferenceNo(referenceNo);
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
        if (!batch.isEmpty()) {
            txnRepo.saveAll(batch);
        }
        return new FileSummary(totalCount, totalAmount);
    }
    private FileSummary parseAndSaveSettlementFile(Sheet worksheet, SourceType sourceType, UploadedFileEntity uploadedFile) throws Exception {
        Row row;
        List<TransactionEntity> batch = new ArrayList<>();
        String legacyId= "";
        BigDecimal amount;
        int totalCount = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (int rowIndex = 8; rowIndex <= worksheet.getLastRowNum(); rowIndex++){
            row = worksheet.getRow(rowIndex);
            if(row == null) continue;
            String cellB = getCellValueAsString(row.getCell(1)).trim();
            String cellC = getCellValueAsString(row.getCell(4)).trim();
            String cellW = getCellValueAsString(row.getCell(22)).trim();
            if (cellC.contains("Legacy ID :")) legacyId = getCellValueAsString(row.getCell(8)).trim();
            if(cellB.trim().isEmpty() && cellC.trim().isEmpty()) continue;
            if(cellW.equals("trn")) {
                String cellValueAmount = getCellValueAsString(row.getCell(23)).trim().replace("-", "");
                amount = (cellValueAmount.isEmpty()) ? BigDecimal.ZERO : new BigDecimal(cellValueAmount);
                if (amount.compareTo(BigDecimal.ZERO) == 0) continue;
                String referenceNo = getCellValueAsString(row.getCell(7)).trim();
                String transactionNo = getCellValueAsString(row.getCell(3)).trim();
                String originatingCountry = getCellValueAsString(row.getCell(11)).trim();
                LocalDate paidDate = convertStringToLocalDate(cellB, "MM/dd/yyyy");
                TransactionEntity txn = new TransactionEntity();
                txn.setTransactionNo(transactionNo);
                txn.setReferenceNo(referenceNo);
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
        if (!batch.isEmpty()) {
            txnRepo.saveAll(batch);
        }
        return new FileSummary(totalCount, totalAmount);
    }

    public static Workbook getWorkbook(InputStream is) throws IOException {
        try {
            // WorkbookFactory automatically detects whether it's .xls or .xlsx
            return WorkbookFactory.create(is);
        } catch (Exception e) {
            throw new IOException("Failed to open the Excel file. Please ensure it's in .xls or .xlsx format.", e);
        }
    }
    public static String getCellValueAsString(Cell cell){
        String str = "";
        if (cell == null) return str;
        switch (cell.getCellType()){
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    return BigDecimal.valueOf(cell.getNumericCellValue()).toPlainString();
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                break;
        }
        return str;
    }
    public static LocalDate convertStringToLocalDate(String date, String format){
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
        try{
            return LocalDate.parse(date, formatter);
        }catch(DateTimeException e){
            e.printStackTrace();
            return null;
        }
    }
    private void saveSinglePaymentFile(MultipartFile file, SourceType sourceType) throws Exception {
        // 1️⃣ Check if file already exists in STAGED or PROCESSED
        Optional<UploadedFileEntity> existingFile = uploadedFileRepository
                .findByFileNameAndSourceType(file.getOriginalFilename(), sourceType);

        if (existingFile.isPresent()) {
            throw new RuntimeException("File '" + file.getOriginalFilename() + "' already uploaded for " + sourceType);
        }
        // 1️⃣ Save file metadata
        UploadedFileEntity uploadedFile = new UploadedFileEntity();
        uploadedFile.setFileName(file.getOriginalFilename());
        uploadedFile.setSourceType(sourceType);
        uploadedFile.setUploadTime(LocalDateTime.now());
        uploadedFile.setStatus(FileStatus.STAGED);

        uploadedFile = uploadedFileRepository.save(uploadedFile);

        // 2️⃣ Parse and save transactions
        InputStream payment = file.getInputStream();
        Workbook recordsPayments = getWorkbook(payment);
        Sheet worksheetPayment = recordsPayments.getSheetAt(0);
        String paymentFileName = file.getOriginalFilename();
        //  get summary from parser
        FileSummary summary = parseAndSavePaymentFile(worksheetPayment, sourceType, uploadedFile);
        //  update file totals
        uploadedFile.setTotalTransactions(summary.getTotalCount());
        uploadedFile.setTotalAmount(summary.getTotalAmount());
        uploadedFileRepository.save(uploadedFile);
    }
    private void saveSingleSettlementFile(MultipartFile file, SourceType sourceType) throws Exception {
        // 1️⃣ Check if file already exists in STAGED or PROCESSED
        Optional<UploadedFileEntity> existingFile = uploadedFileRepository
                .findByFileNameAndSourceType(file.getOriginalFilename(), sourceType);

        if (existingFile.isPresent()) {
            throw new RuntimeException("File '" + file.getOriginalFilename() + "' already uploaded for " + sourceType);
        }
        // 1️⃣ Save file metadata
        UploadedFileEntity uploadedFile = new UploadedFileEntity();
        uploadedFile.setFileName(file.getOriginalFilename());
        uploadedFile.setSourceType(sourceType);
        uploadedFile.setUploadTime(LocalDateTime.now());
        uploadedFile.setStatus(FileStatus.STAGED);

        uploadedFile = uploadedFileRepository.save(uploadedFile);

        // 2️⃣ Parse and save transactions
        InputStream settlement = file.getInputStream();
        Workbook recordsSettlement = getWorkbook(settlement);
        Sheet worksheetSettlement = recordsSettlement.getSheetAt(0);
        String settlementFileName = file.getOriginalFilename();
        //  get summary from parser
        FileSummary summary = parseAndSaveSettlementFile(worksheetSettlement, sourceType, uploadedFile);
        //  update file totals
        uploadedFile.setTotalTransactions(summary.getTotalCount());
        uploadedFile.setTotalAmount(summary.getTotalAmount());
        uploadedFileRepository.save(uploadedFile);
    }
    public void deleteMultipleFiles(List<Integer> fileIds) {
        // Fetch the entities first so Hibernate can manage the cascade deletion
        List<UploadedFileEntity> filesToDelete = uploadedFileRepository.findAllById(fileIds);

        if (!filesToDelete.isEmpty()) {
            // This triggers the orphanRemoval and CascadeType.ALL logic
            uploadedFileRepository.deleteAll(filesToDelete);
        }
    }

    @Transactional
    public void deleteUploadedFile(Integer uploadedFileId) {
        UploadedFileEntity file = uploadedFileRepository.findById(uploadedFileId)
                .orElseThrow(() -> new RuntimeException("File not found"));

        // Delete only staged transactions, not reconciled or unmatched
        txnRepo.deleteByUploadedFileAndReconStatus(file, ReconStatus.S);

        // Delete the file record
        uploadedFileRepository.delete(file);
    }
    public List<UploadedFileEntity> findByStatus(FileStatus fileStatus){
        return uploadedFileRepository.findByStatus(fileStatus);
    }
}