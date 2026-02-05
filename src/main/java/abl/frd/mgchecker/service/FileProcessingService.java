package abl.frd.mgchecker.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;

@Service
public class FileProcessingService {

    public void processFile(MultipartFile file, String source) {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(file.getInputStream()))) {

            String line;
            while ((line = br.readLine()) != null) {
                // Each line = one row
                System.out.println(source + " -> " + line);

                // Later:
                // parse line
                // map to entity
                // save to MySQL
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to process file: " + file.getOriginalFilename(), e);
        }
    }
    public static boolean hasCSVFormat(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType != null &&
                (contentType.equalsIgnoreCase("text/csv") ||
                        contentType.equalsIgnoreCase("application/csv") ||
                        contentType.equalsIgnoreCase("application/vnd.ms-excel") ||
                        contentType.equalsIgnoreCase("text/plain") ||
                        contentType.equalsIgnoreCase("application/vnd.oasis.opendocument.spreadsheet")||
                        contentType.equalsIgnoreCase("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }
}
