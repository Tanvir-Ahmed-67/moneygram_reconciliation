package abl.frd.mgchecker.controller;

import abl.frd.mgchecker.enumpack.FileStatus;
import abl.frd.mgchecker.enumpack.ReconStatus;
import abl.frd.mgchecker.model.TransactionEntity;
import abl.frd.mgchecker.model.UploadedFileEntity;
import abl.frd.mgchecker.repository.TransactionRepository;
import abl.frd.mgchecker.service.FileUploadService;
import abl.frd.mgchecker.service.ReconciliationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class FileUploadController {

    private final FileUploadService fileUploadService;
    private final ReconciliationService reconciliationService;
    private final TransactionRepository txnRepo;

    public FileUploadController(FileUploadService fileUploadService, TransactionRepository txnRepo, ReconciliationService reconciliationService) {
        this.fileUploadService = fileUploadService;
        this.txnRepo = txnRepo;
        this.reconciliationService = reconciliationService;
    }
    @GetMapping("/")
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

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @PostMapping("/upload")
    public String handleFileUpload(
            @RequestParam(value = "payment_file", required = false)
            List<MultipartFile> paymentFiles,

            @RequestParam(value = "settlement_file", required = false)
            List<MultipartFile> settlementFiles,

            RedirectAttributes redirectAttributes) {
        try {
            List<UploadedFileEntity> stagedFiles = fileUploadService.findByStatus(FileStatus.STAGED);
            redirectAttributes.addFlashAttribute("stagedFiles", stagedFiles);
            // 1. Check if both lists are null/empty OR if the first element is actually empty
            boolean noPayment = isFileMissing(paymentFiles);
            boolean noSettlement = isFileMissing(settlementFiles);

            if (noPayment && noSettlement) {
                redirectAttributes.addFlashAttribute("message", "Error: No File Selected! Please Choose Atleast one!");
                return "redirect:/";
            }
            fileUploadService.processFiles(paymentFiles, settlementFiles);
            redirectAttributes.addFlashAttribute("message", "Files uploaded successfully! Click Process to reconcile.");
            return "redirect:/";
        }
        catch (Exception e) {
            redirectAttributes.addFlashAttribute("message", "Error: " + e.getMessage());
            return "redirect:/";
        }
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
    public String showResults(Model model) {
        // 1️⃣ Get all Unreconciled Transactions (Status 'U')
        List<TransactionEntity> allUnreconciled = txnRepo.findByReconStatus(ReconStatus.U);

        // 2️⃣ Sum all unmatched amounts
        BigDecimal totalAllUnmatched = txnRepo.sumAllUnmatchedAmount();

        // 3️⃣ Determine the latest transaction date (up to which date data is shown)
        String upToDate = allUnreconciled.stream()
                .map(TransactionEntity::getTransactionDate)
                .max(String::compareTo) // assuming transactionDate stored as 'yyyy-MM-dd' string
                .orElse("N/A");

        // 4️⃣ Add attributes to model
        model.addAttribute("allData", allUnreconciled);
        model.addAttribute("allTotal", totalAllUnmatched);
        model.addAttribute("upToDate", upToDate);

        return "result";
    }
    @PostMapping("/file/delete/{id}")
    public String deleteFile(@PathVariable Integer id, RedirectAttributes redirectAttributes) {
        try {
            fileUploadService.deleteUploadedFile(id);
            redirectAttributes.addFlashAttribute("message", "File deleted successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("message", "Error deleting file: " + e.getMessage());
        }
        return "redirect:/upload";
    }
    @PostMapping("/delete-files")
    public String deleteFiles(@RequestParam(value = "fileIds", required = false) List<Integer> fileIds,
                              RedirectAttributes redirectAttributes) {
        if (fileIds != null && !fileIds.isEmpty()) {
            fileUploadService.deleteMultipleFiles(fileIds);
            redirectAttributes.addFlashAttribute("message", "Selected files removed.");
        }
        return "redirect:/";
    }
    /**
     * Helper to check if the uploaded list is truly empty or contains a blank file
     */
    private boolean isFileMissing(List<MultipartFile> files) {
        return files == null || files.isEmpty() || files.get(0).isEmpty();
    }

}