package abl.frd.mgchecker.controller;

import abl.frd.mgchecker.service.FileProcessingService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class FileUploadController {

    private final FileProcessingService service;

    public FileUploadController(FileProcessingService service) {
        this.service = service;
    }

    @GetMapping("/")
    public String uploadPage() {
        return "upload";
    }

    @PostMapping("/upload")
    public String handleUpload(@RequestParam("file1") MultipartFile file1, @RequestParam("file2") MultipartFile file2, Model model) {
        if (service.hasCSVFormat(file1) && service.hasCSVFormat(file2)) {
            // have to check if file_1 is a payment file and file_2 is a settlement file. Vice versa will give error
            service.processFile(file1, "FILE_1");
            service.processFile(file2, "FILE_2");
            model.addAttribute("message", "Files processed successfully!");
        }
        return "upload";
    }
}
