package abl.frd.mgchecker.controller;

import abl.frd.mgchecker.enumpack.ReconStatus;
import abl.frd.mgchecker.model.TransactionEntity;
import abl.frd.mgchecker.repository.TransactionRepository;
import abl.frd.mgchecker.service.FileUploadService;
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
    private final TransactionRepository txnRepo;

    public FileUploadController(FileUploadService fileUploadService, TransactionRepository txnRepo) {
        this.fileUploadService = fileUploadService;
        this.txnRepo = txnRepo;
    }

    @GetMapping("/")
    public String showUploadPage() {
        return "upload";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @PostMapping("/upload")
    public String handleFileUpload(
            @RequestParam("payment_file") MultipartFile paymentFile,
            @RequestParam("settlement_file") MultipartFile settlementFile,
            RedirectAttributes redirectAttributes) {
        try {
            fileUploadService.processFiles(paymentFile, settlementFile);
            // Flash attributes survive the redirect to the result page
            redirectAttributes.addFlashAttribute("message", "Files uploaded and reconciled successfully!");
            return "redirect:/result";
        }
        catch (Exception e) {
            redirectAttributes.addFlashAttribute("message", "Error: " + e.getMessage());
            return "redirect:/";
        }
    }

    @GetMapping("/result")
    public String showResults(Model model) {
        // 1. Get all Unreconciled Transactions (Status 'U')
        List<TransactionEntity> allUnreconciled = txnRepo.findByReconStatus(ReconStatus.U);

        // 2. Get today's date in String format
        String todayStr = LocalDate.now().toString();

        // 3. Filter for Today's Unreconciled Data based on fileUploadDate
        List<TransactionEntity> todayUnreconciled = allUnreconciled.stream()
                .filter(txn -> txn.getFileUploadDate() != null && txn.getFileUploadDate().equals(todayStr))
                .collect(Collectors.toList());
        BigDecimal totalAllUnmatched =
                txnRepo.sumAllUnmatchedAmount();

        BigDecimal totalTodayUnmatched =
                txnRepo.sumTodayUnmatchedAmount(todayStr);

        model.addAttribute("todayData", todayUnreconciled);
        model.addAttribute("allData", allUnreconciled);
        model.addAttribute("allTotal", totalAllUnmatched);
        model.addAttribute("todayTotal", totalTodayUnmatched);

        return "result";
    }
}