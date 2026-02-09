package abl.frd.mgchecker.service;

import abl.frd.mgchecker.enumpack.ReconStatus;
import abl.frd.mgchecker.enumpack.SourceType;
import abl.frd.mgchecker.model.TransactionEntity;
import abl.frd.mgchecker.repository.TransactionRepository;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        System.out.println(".........Inside Process File");
        InputStream payment = paymentFile.getInputStream();
        Workbook recordsPayments = getWorkbook(payment);
        Sheet worksheetPayment = recordsPayments.getSheetAt(0);

        InputStream settlement = settlementFile.getInputStream();
        Workbook recordsSettlement = getWorkbook(settlement);
        Sheet worksheetSettlement = recordsSettlement.getSheetAt(0);

        // 1️⃣ Parse Payment file
        parseAndSavePaymentFile(worksheetPayment, SourceType.PAYMENT);

        // 2️⃣ Parse Settlement file
        parseAndSaveSettlementFile(worksheetSettlement, SourceType.SETTLEMENT);
        System.out.println(".........After method parseAndSaveSettlementFile File");

        // 3️⃣ Run reconciliation including unmatched re-check
        reconciliationService.reconcileIncremental();
    }

    private void parseAndSavePaymentFile(Sheet worksheet, SourceType sourceType) throws Exception {
        Map<String, Object> resp = new HashMap<>();
        Row row;
        List<Map<String, Object>> dataList = new ArrayList<>();
        List<TransactionEntity> batch = new ArrayList<>();
        List<String[]> uniqueKeys = new ArrayList<>();
        String legacyId= "";
        for (int rowIndex = 6; rowIndex <= worksheet.getLastRowNum(); rowIndex++){
            row = worksheet.getRow(rowIndex);
            if(row == null) continue;
            String cellB = getCellValueAsString(row.getCell(1)).trim();
            String cellC = getCellValueAsString(row.getCell(2)).trim();
            if(cellB.trim().isEmpty() && cellC.trim().isEmpty()) continue;
            if(cellC.contains("Legacy ID :")) legacyId = getCellValueAsString(row.getCell(6)).trim();
            if(cellB.contains("Account Number :") || cellB.isEmpty() || cellB.contains("Settlement Currency :")) continue;
            Double amount = Double.valueOf(getCellValueAsString(row.getCell(25)).trim().replace("-", ""));
            if(amount==0.0) continue;
            String transactionNo = getCellValueAsString(row.getCell(8)).trim();
            LocalDate paidDate = convertStringToLocalDate(cellB, "MM/dd/yyyy");
            TransactionEntity txn = new TransactionEntity();
            txn.setTransactionNo(transactionNo);
            txn.setAmount(amount);
            txn.setLegacyId(legacyId);
            txn.setTransactionDate(String.valueOf(paidDate));
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
    private void parseAndSaveSettlementFile(Sheet worksheet, SourceType sourceType) throws Exception {
        Map<String, Object> resp = new HashMap<>();
        Row row;
        List<Map<String, Object>> dataList = new ArrayList<>();
        List<TransactionEntity> batch = new ArrayList<>();
        List<String[]> uniqueKeys = new ArrayList<>();
        String legacyId= "";
        Double amount=0.0;
        for (int rowIndex = 8; rowIndex <= worksheet.getLastRowNum(); rowIndex++){
            row = worksheet.getRow(rowIndex);
            if(row == null) continue;
            String cellB = getCellValueAsString(row.getCell(1)).trim();
            String cellC = getCellValueAsString(row.getCell(4)).trim();
            String cellW = getCellValueAsString(row.getCell(22)).trim();
            if (cellC.contains("Legacy ID :")) legacyId = getCellValueAsString(row.getCell(8)).trim();
            if(cellB.trim().isEmpty() && cellC.trim().isEmpty()) continue;
            //if(cellB.contains("Account Number :") || cellB.isEmpty() || cellB.contains("Settlement Currency :")) continue;
            if(cellW.equals("trn")) {
                amount = Double.valueOf(getCellValueAsString(row.getCell(23)).trim().replace("-", ""));
                if (amount == 0.0) continue;
                String transactionNo = getCellValueAsString(row.getCell(7)).trim();
                LocalDate paidDate = convertStringToLocalDate(cellB, "MM/dd/yyyy");
                TransactionEntity txn = new TransactionEntity();
                txn.setTransactionNo(transactionNo);
                System.out.println(transactionNo + "........." + amount);
                txn.setAmount(amount);
                txn.setLegacyId(legacyId);
                txn.setTransactionDate(String.valueOf(paidDate));
                txn.setFileUploadDate(String.valueOf(LocalDateTime.now()));
                txn.setSourceType(sourceType);
                txn.setReconStatus(ReconStatus.N);
                batch.add(txn);
            }
            if (batch.size() >= 500) {
                txnRepo.saveAll(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {

            txnRepo.saveAll(batch);
        }
    }

    private void parseAndSave_1(MultipartFile file, SourceType sourceType) throws Exception {
        System.out.println(".........Inside parseAndSave method");
        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            List<TransactionEntity> batch = new ArrayList<>();
            while ((line = br.readLine()) != null) {
                String[] cols = line.split(";");

                // Skip header if present
                if (cols[0].equalsIgnoreCase("transactionNo")) continue;

                TransactionEntity txn = new TransactionEntity();
                txn.setTransactionNo(cols[0].trim());
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
    @Transactional
    public void reconcile() {

        // 1. fetch unprocessed PAYMENT txns
        // 2. join with SETTLEMENT txns (native query / JPQL)
        // 3. insert matches
        // 4. update reconStatus
        // 5. insert unmatched references
    }

}
