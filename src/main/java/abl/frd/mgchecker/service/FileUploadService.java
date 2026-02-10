package abl.frd.mgchecker.service;

import abl.frd.mgchecker.enumpack.ReconStatus;
import abl.frd.mgchecker.enumpack.SourceType;
import abl.frd.mgchecker.model.ReconciliationUnmatched;
import abl.frd.mgchecker.model.TransactionEntity;
import abl.frd.mgchecker.repository.TransactionRepository;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
    public byte[] generateCsv(List<ReconciliationUnmatched> rows) {
        StringBuilder csv = new StringBuilder();
        csv.append("Serial,Transaction Date,Transaction No,Legacy Id,Amount\n"); // Header
        for (ReconciliationUnmatched row : rows) {
            csv.append(String.format("%s,%s,%s,%s,%s\n",
                    row.getId(), "", "", "", 1000));
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }
    public byte[] generateExcel(List<ReconciliationUnmatched> rows) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Unreconciled");

        // Create header row
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Serial");
        header.createCell(1).setCellValue("Transaction Date");
        header.createCell(2).setCellValue("Transaction No");
        header.createCell(2).setCellValue("Legacy Id");
        header.createCell(2).setCellValue("Amount");

        // Fill data
        int rowIdx = 1;
        for (ReconciliationUnmatched row : rows) {
            Row excelRow = sheet.createRow(rowIdx++);
            excelRow.createCell(0).setCellValue(row.getId());
            //excelRow.createCell(1).setCellValue(row.getDate().toString());
           // excelRow.createCell(2).setCellValue(row.getAmount().doubleValue());
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();
        return out.toByteArray();
    }

}
