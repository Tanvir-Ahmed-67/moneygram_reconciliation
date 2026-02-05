package abl.frd.mgchecker.controller;

import abl.frd.mgchecker.service.FileUploadService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping("/upload")
    public String handleFileUpload(
            @RequestParam("payment_file") MultipartFile paymentFile,
            @RequestParam("settlement_file") MultipartFile settlementFile,
            Model model) {

        try {
            fileUploadService.processFiles(paymentFile, settlementFile);
            model.addAttribute("message", "Files uploaded and reconciled successfully!");
        } catch (Exception e) {
            model.addAttribute("message", "Error: " + e.getMessage());
        }

        return "upload";
    }
}
