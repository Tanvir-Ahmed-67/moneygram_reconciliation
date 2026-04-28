package abl.frd.mgchecker.controller;

import abl.frd.mgchecker.enumpack.FileStatus;
import abl.frd.mgchecker.enumpack.ReconStatus;
import abl.frd.mgchecker.enumpack.SourceType;
import abl.frd.mgchecker.helper.ReconSummary;
import abl.frd.mgchecker.model.FundEntity;
import abl.frd.mgchecker.model.TransactionEntity;
import abl.frd.mgchecker.model.UploadedFileEntity;
import abl.frd.mgchecker.repository.FundRepository;
import abl.frd.mgchecker.repository.TransactionRepository;
import abl.frd.mgchecker.repository.UploadedFileRepository;
import abl.frd.mgchecker.service.FileUploadService;
import abl.frd.mgchecker.service.FundService;
import abl.frd.mgchecker.service.ReconciliationService;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class FileUploadController {

    private final FileUploadService fileUploadService;
    private final ReconciliationService reconciliationService;
    private final TransactionRepository txnRepo;
    private final UploadedFileRepository uploadedFileRepository;
    private final FundService fundService;
    private final FundRepository fundRepository;

    public FileUploadController(FileUploadService fileUploadService, TransactionRepository txnRepo,
            ReconciliationService reconciliationService, FundService fundService,
            UploadedFileRepository uploadedFileRepository, FundRepository fundRepository) {
        this.fileUploadService = fileUploadService;
        this.txnRepo = txnRepo;
        this.reconciliationService = reconciliationService;
        this.fundService = fundService;
        this.uploadedFileRepository = uploadedFileRepository;
        this.fundRepository = fundRepository;
    }

    @GetMapping("/")
    public String index() {
        return "redirect:/files"; // This will make the root URL go to your Home Page
    }

    @GetMapping("/upload-file")
    public String showUploadPage(Model model) {
        // Fetch all files that are currently STAGED
        List<UploadedFileEntity> allStaged = fileUploadService.findByStatus(FileStatus.STAGED);
        List<UploadedFileEntity> paymentFiles = allStaged.stream()
                .filter(f -> "PAYMENT".equals(f.getSourceType().name()))
                .collect(Collectors.toList());
        List<UploadedFileEntity> settlementFiles = allStaged.stream()
                .filter(f -> "SETTLEMENT".equals(f.getSourceType().name()))
                .collect(Collectors.toList());

        model.addAttribute("stagedPaymentFiles", paymentFiles);
        model.addAttribute("stagedSettlementFiles", settlementFiles);
        return "upload";
    }

    @GetMapping("/fund-input")
    public String showFundInputPage(Model model) {
        List<UploadedFileEntity> settlementFiles = uploadedFileRepository.findUnmappedFilesBySourceType(SourceType.SETTLEMENT);
        model.addAttribute("settlementFiles", settlementFiles);
        return "fund-input";
    }

    @GetMapping("/fund-list")
    public String showFundListPage(Model model) {
        model.addAttribute("funds", fundService.getAllFunds());
        return "fund-list";
    }

    @PostMapping("/fund-edit")
    public String handleFundEdit(
            @RequestParam("id") Integer id,
            @RequestParam("valueDate") String valueDate,
            @RequestParam("referenceNo") String referenceNo,
            @RequestParam("amountUsd") BigDecimal amountUsd,
            @RequestParam("conversionRate") BigDecimal conversionRate,
            @RequestParam("amountBdt") BigDecimal amountBdt,
            RedirectAttributes redirectAttributes) {

        fundService.updateFund(id, valueDate, referenceNo, amountUsd, conversionRate, amountBdt);

        redirectAttributes.addFlashAttribute("message",
                "<div class='text-success mb-0'><strong>Success:</strong> Fund details updated successfully.</div>");
        return "redirect:/fund-list";
    }

    @PostMapping("/fund-delete/{id}")
    public String handleFundDelete(
            @PathVariable Integer id,
            RedirectAttributes redirectAttributes) {

        fundService.deleteFund(id);

        redirectAttributes.addFlashAttribute("message",
                "<div class='text-success mb-0'><strong>Success:</strong> Fund deleted successfully.</div>");
        return "redirect:/fund-list";
    }

    @PostMapping("/fund-submit")
    public String handleFundSubmit(
            @RequestParam("settlementFileId") Long fileId,
            @RequestParam("valueDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate valueDate,
            @RequestParam("referenceNo") String referenceNo,
            @RequestParam("amountUsd") BigDecimal amountUsd,
            @RequestParam("conversionRate") BigDecimal conversionRate,
            @RequestParam("amountBdt") BigDecimal amountBdt,
            RedirectAttributes redirectAttributes) {
        FundEntity fund = new FundEntity();
        fund.setValueDate(valueDate.toString());
        fund.setReferenceNo(referenceNo);
        fund.setAmountUsd(amountUsd);
        fund.setConversionRate(conversionRate);
        fund.setAmountBdt(amountBdt);

        fundService.saveFund(fund);

        redirectAttributes.addFlashAttribute("message",
                "<div class='text-success mb-0'><strong>Success:</strong> Fund details for " + valueDate
                        + " submitted successfully.</div>");
        return "redirect:/fund-input";
    }

    @PostMapping("/truncate-all")
    public String truncateAll(@RequestParam("adminPassword") String password, RedirectAttributes ra) {

        // Replace with your actual password
        if (!"M@g#123".equals(password)) {
            ra.addFlashAttribute("message", "Invalid Password! Operation Aborted.");
            return "redirect:/file-history";
        }

        try {
            // Clear children then parents
            fileUploadService.deleteAllInBatch();
            ra.addFlashAttribute("message", "All tables wiped successfully.");
        } catch (Exception e) {
            ra.addFlashAttribute("message", "System Error:" + e.getMessage() + " ");
        }
        // Redirect to the GET mapping that displays the page
        return "redirect:/file-history";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @PostMapping("/upload")
    public String handleFileUpload(
            @RequestParam(value = "payment_file", required = false) List<MultipartFile> paymentFiles,
            @RequestParam(value = "settlement_file", required = false) List<MultipartFile> settlementFiles,
            RedirectAttributes redirectAttributes) {
        try {
            if (isFileMissing(paymentFiles) && isFileMissing(settlementFiles)) {
                redirectAttributes.addFlashAttribute("message",
                        "<div class='text-danger'><strong>Error: No file selected!</strong><ul class='mb-0'>");
                return "redirect:/upload-file";
            }

            // Capture the result message from the service
            String resultMsg = fileUploadService.processFiles(paymentFiles, settlementFiles);
            redirectAttributes.addFlashAttribute("message", resultMsg);
            return "redirect:/upload-file";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("message",
                    "<div class='text-danger'><strong>System Error:</strong><ul class='mb-0'>" + e.getMessage());
            return "redirect:/upload-file";
        }
    }

    @GetMapping("/summary")
    public String showSummary(
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            Model model) {

        // Fetch all records
        List<TransactionEntity> allTransactions = txnRepo.findAll();

        // 1. Apply Filter (only if values are present)
        List<TransactionEntity> filtered = allTransactions.stream()
                .filter(t -> t.getReconStatus() == ReconStatus.M || t.getReconStatus() == ReconStatus.U)
                .filter(t -> t.getTransactionDate() != null)
                .filter(t -> (startDate == null || startDate.isEmpty()
                        || t.getTransactionDate().compareTo(startDate) >= 0))
                .filter(t -> (endDate == null || endDate.isEmpty() || t.getTransactionDate().compareTo(endDate) <= 0))
                .collect(Collectors.toList());

        // 2. Group by Transaction Date
        Map<String, List<TransactionEntity>> groupedByDate = filtered.stream()
                .collect(Collectors.groupingBy(TransactionEntity::getTransactionDate));

        // 3. Map to DTOs
        List<ReconSummary> summaryRows = groupedByDate.entrySet().stream()
                .map(entry -> {
                    String date = entry.getKey();
                    List<TransactionEntity> list = entry.getValue();

                    long pCount = list.stream().filter(t -> t.getSourceType() == SourceType.PAYMENT).count();
                    BigDecimal pAmt = list.stream().filter(t -> t.getSourceType() == SourceType.PAYMENT)
                            .map(TransactionEntity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

                    long sCount = list.stream().filter(t -> t.getSourceType() == SourceType.SETTLEMENT).count();
                    BigDecimal sAmt = list.stream().filter(t -> t.getSourceType() == SourceType.SETTLEMENT)
                            .map(TransactionEntity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

                    return new ReconSummary(date, pCount, pAmt, sCount, sAmt);
                })
                .sorted((a, b) -> b.getDate().compareTo(a.getDate()))
                .collect(Collectors.toList());

        // 4. Calculate Grand Totals (Calculated from summaryRows to reflect current
        // view)
        long totalDiffCount = summaryRows.stream().mapToLong(ReconSummary::getTxnDiff).sum();
        BigDecimal totalDiffAmount = summaryRows.stream()
                .map(ReconSummary::getAmtDiff)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("summaryRows", summaryRows);
        model.addAttribute("totalDiffCount", totalDiffCount);
        model.addAttribute("totalDiffAmount", totalDiffAmount);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);

        return "summary";
    }

    @PostMapping("/process-files")
    public String processReconciliation(RedirectAttributes redirectAttributes) {
        try {
            reconciliationService.reconcileIncremental();
            redirectAttributes.addFlashAttribute("message", "Reconciliation completed successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("message", "Error during reconciliation: " + e.getMessage());
        }
        return "redirect:/result";
    }

    @GetMapping("/result")
    public String showResults(
            @RequestParam(value = "date", required = false) String date,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            Model model) {

        List<TransactionEntity> allUnreconciled = txnRepo.findByReconStatus(ReconStatus.U);
        List<TransactionEntity> filteredData = allUnreconciled;
        String upToDate = "N/A";
        boolean isFiltered = false;

        // SCENARIO 1: Coming from Summary Page (Specific Date)
        if (date != null && !date.isEmpty()) {
            filteredData = allUnreconciled.stream()
                    .filter(t -> date.equals(t.getTransactionDate()))
                    .collect(Collectors.toList());
            upToDate = date;
            isFiltered = true;
        }
        // SCENARIO 2: Filtered by Date Range on Result Page
        else if ((startDate != null && !startDate.isEmpty()) || (endDate != null && !endDate.isEmpty())) {
            filteredData = allUnreconciled.stream()
                    .filter(t -> t.getTransactionDate() != null)
                    .filter(t -> (startDate == null || startDate.isEmpty()
                            || t.getTransactionDate().compareTo(startDate) >= 0))
                    .filter(t -> (endDate == null || endDate.isEmpty()
                            || t.getTransactionDate().compareTo(endDate) <= 0))
                    .collect(Collectors.toList());

            upToDate = (startDate != null ? startDate : "...") + " to " + (endDate != null ? endDate : "...");
            isFiltered = true;
        }
        // SCENARIO 3: View All
        else {
            upToDate = allUnreconciled.stream()
                    .map(TransactionEntity::getTransactionDate)
                    .filter(java.util.Objects::nonNull)
                    .max(String::compareTo)
                    .orElse("N/A");
        }

        BigDecimal total = filteredData.stream()
                .map(TransactionEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("allData", filteredData);
        model.addAttribute("allTotal", total);
        model.addAttribute("upToDate", upToDate);
        model.addAttribute("isFiltered", isFiltered);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);

        return "result";
    }

    @GetMapping("/files")
    public String showFilesSummary(Model model) {
        return "files-summary";
    }

    @GetMapping("/file-history")
    public String showFileHistory(
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Model model) {
        List<UploadedFileEntity> allFiles;
        if (startDate != null && endDate != null) {
            LocalDateTime startDay = startDate.atStartOfDay();
            LocalDateTime endDay = endDate.atTime(LocalTime.MAX);
            // Fetch only files for the selected date
            allFiles = fileUploadService.findByUploadDate(startDay, endDay);
        } else {
            // Fetch all files
            allFiles = fileUploadService.findAll();
        }
        // List<UploadedFileEntity> allFiles = fileUploadService.findAll();
        if (allFiles == null)
            allFiles = new ArrayList<>();

        // Grouping Payments by Date
        Map<LocalDate, List<UploadedFileEntity>> paymentGroups = allFiles.stream()
                .filter(f -> f.getSourceType() == SourceType.PAYMENT)
                .collect(Collectors.groupingBy(
                        f -> f.getUploadTime().toLocalDate(),
                        TreeMap::new, // Keeps dates sorted
                        Collectors.toList()));

        // Grouping Settlements by Date
        Map<LocalDate, List<UploadedFileEntity>> settlementGroups = allFiles.stream()
                .filter(f -> f.getSourceType() == SourceType.SETTLEMENT)
                .collect(Collectors.groupingBy(
                        f -> f.getUploadTime().toLocalDate(),
                        TreeMap::new,
                        Collectors.toList()));

        model.addAttribute("paymentGroups", paymentGroups);
        model.addAttribute("settlementGroups", settlementGroups);

        return "file-history";
    }

    @GetMapping("/download-file/{id}")
    public ResponseEntity<Resource> downloadFile(@PathVariable int id) {
        // 1. Fetch the file metadata
        UploadedFileEntity fileEntity = fileUploadService.findById(id);
        if (fileEntity == null) {
            throw new RuntimeException("File not found with ID: " + id);
        }
        // 2. Fetch the transactions associated with this file
        // Assuming your entity has: List<TransactionEntity> transactions
        List<TransactionEntity> transactions = fileEntity.getTransactions();

        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Transactions");

            // Create Header Row
            Row headerRow = sheet.createRow(0);
            String[] columns = { "Serial", "Legacy ID", "Txn No", "Date", "Country", "Ref No", "Amount" };
            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
            }

            // Fill Data Rows
            int rowIdx = 1;
            for (TransactionEntity txn : transactions) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(rowIdx - 1);
                row.createCell(1).setCellValue(txn.getLegacyId());
                row.createCell(2).setCellValue(txn.getTransactionNo());
                row.createCell(3).setCellValue(txn.getTransactionDate());
                row.createCell(4).setCellValue(txn.getOriginatingCountry());
                row.createCell(5).setCellValue(txn.getReferenceNo());
                row.createCell(6).setCellValue(txn.getAmount().doubleValue());
            }

            workbook.write(out);
            ByteArrayResource resource = new ByteArrayResource(out.toByteArray());

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=Records_" + fileEntity.getFileName() + ".xlsx")
                    .contentType(MediaType
                            .parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(resource);

        } catch (IOException e) {
            throw new RuntimeException("Fail to export data to Excel file: " + e.getMessage());
        }
    }

    @PostMapping("/file/delete/{id}")
    public String deleteFile(
            @PathVariable Integer id,
            @RequestParam("adminPassword") String adminPassword,
            RedirectAttributes redirectAttributes) {
        final String REQUIRED_PASSWORD = "M@g#123";
        if (!REQUIRED_PASSWORD.equals(adminPassword)) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Unauthorized: Incorrect Admin Password. Deletion failed.");
            return "redirect:/file-history";
        }
        try {
            UploadedFileEntity file = fileUploadService.findById(id);
            if (file == null)
                return "redirect:/file-history";
            boolean wasProcessed = (file.getStatus() == FileStatus.PROCESSED);

            // Run the working Sequential Solution we established
            fileUploadService.deleteUploadedFile(id);

            if (wasProcessed) {
                reconciliationService.reconcileIncremental();
                redirectAttributes.addFlashAttribute("message", "File deleted and reconciliation updated.");
            } else {
                redirectAttributes.addFlashAttribute("message", "Staged file removed.");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error during deletion: " + e.getMessage());
        }
        return "redirect:/file-history";
    }

    @PostMapping("/delete-files")
    public String deleteFiles(@RequestParam(value = "fileIds", required = false) List<Integer> fileIds,
            RedirectAttributes redirectAttributes) {
        if (fileIds != null && !fileIds.isEmpty()) {
            fileUploadService.deleteMultipleFiles(fileIds);
            redirectAttributes.addFlashAttribute("message", "Selected files removed.");
        }
        return "redirect:/upload-file";
    }

    // For the Modal View (returns JSON)
    @GetMapping("/files/view/{id}")
    @ResponseBody
    public List<TransactionEntity> getFileData(@PathVariable int id) {
        UploadedFileEntity file = fileUploadService.findById(id);
        return file != null ? file.getTransactions() : new ArrayList<>();
    }

    /**
     * Helper to check if the uploaded list is truly empty or contains a blank file
     */
    private boolean isFileMissing(List<MultipartFile> files) {
        return files == null || files.isEmpty() || files.get(0).isEmpty();
    }

}