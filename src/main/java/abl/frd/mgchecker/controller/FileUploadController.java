package abl.frd.mgchecker.controller;

import abl.frd.mgchecker.service.FileUploadService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class FileUploadController {

    private final FileUploadService fileUploadService;

    public FileUploadController(FileUploadService fileUploadService) {
        this.fileUploadService = fileUploadService;
    }

    @GetMapping("/")
    public String showUploadPage() {
        return "upload"; // Thymeleaf template name
    }
    @GetMapping("/login")
    public String login() {
        return "login";
    }
    @GetMapping("/results")
    public String showResultsPage(Model model) {
        // Optional: Retrieve previous results from a Service or Session
        // model.addAttribute("unreconciledByDate", fileUploadService.getLatestSummary());
        // model.addAttribute("allUnreconciled", fileUploadService.getLatestDetails());

        return "result"; // Name of your results.html file
    }

    @PostMapping("/upload")
    public String handleFileUpload(
            @RequestParam("payment_file") MultipartFile paymentFile,
            @RequestParam("settlement_file") MultipartFile settlementFile,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            fileUploadService.processFiles(paymentFile, settlementFile);
            model.addAttribute("message", "Files uploaded and reconciled successfully!");
            return "redirect:/result";
        }
        catch (Exception e) {
            model.addAttribute("message", "Error: " + e.getMessage());
            return "redirect:/";
        }
    }
}
