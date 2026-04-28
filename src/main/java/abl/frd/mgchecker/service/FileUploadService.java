package abl.frd.mgchecker.service;

import abl.frd.mgchecker.enumpack.FileStatus;
import abl.frd.mgchecker.enumpack.SourceType;
import abl.frd.mgchecker.model.FundEntity;
import abl.frd.mgchecker.model.UploadedFileEntity;
import abl.frd.mgchecker.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;


@Service
public class FileUploadService {

    private final TransactionRepository txnRepo;
    private final UploadedFileRepository uploadedFileRepository;
    private final ReconciliationService reconciliationService;
    private final ReconciliationUnmatchedRepository reconciliationUnmatchedRepository;
    private final ReconciliationMatchRepository reconciliationMatchedRepository;
    private final FundRepository fundRepository;
    @Autowired
    private FileStorageService fileStorageService;



    public FileUploadService(TransactionRepository txnRepo, ReconciliationService reconciliationService, UploadedFileRepository uploadedFileRepository, ReconciliationUnmatchedRepository reconciliationUnmatchedRepository, ReconciliationMatchRepository reconciliationMatchedRepository,FundRepository fundRepository) {
        this.txnRepo = txnRepo;
        this.reconciliationService = reconciliationService;
        this.uploadedFileRepository = uploadedFileRepository;
        this.reconciliationUnmatchedRepository = reconciliationUnmatchedRepository;
        this.reconciliationMatchedRepository = reconciliationMatchedRepository;
        this.fundRepository = fundRepository;
    }

    public String processFiles(List<MultipartFile> paymentFiles, List<MultipartFile> settlementFiles) {
        List<String> uploaded = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<FileTask> tasks = new ArrayList<>();
        // 2. Collect all tasks
        if (paymentFiles != null) {
            paymentFiles.stream()
                    .filter(f -> !f.isEmpty())
                    .forEach(f -> tasks.add(new FileTask(f, SourceType.PAYMENT)));
        }
        if (settlementFiles != null) {
            settlementFiles.stream()
                    .filter(f -> !f.isEmpty())
                    .forEach(f -> tasks.add(new FileTask(f, SourceType.SETTLEMENT)));
        }
        tasks.forEach(task -> {
            try {
                String result = fileStorageService.saveSingleFileAtomic(task.file, task.sourceType);

                if ("SUCCESS".equals(result)) {
                    uploaded.add(task.file.getOriginalFilename());
                } else {
                    skipped.add("<strong>" + task.file.getOriginalFilename() + "</strong>: " + result);
                }
            } catch (Exception e) {
                e.printStackTrace();
                skipped.add("<strong>" + task.file.getOriginalFilename() + "</strong>: System Error - " + e.getMessage());
            }
        });
        StringBuilder msg = new StringBuilder();

        // Generic Success Message
        if (!uploaded.isEmpty()) {
            msg.append("<div class='text-success mb-2'>Successfully uploaded " + uploaded.size() + " file(s).</div>");
        }
        // Specific Red Error Messages
        if (!skipped.isEmpty()) {
            msg.append("<div class='text-danger'><strong>Upload Issues:</strong><ul class='mb-0'>");
            for (String skipMsg : skipped) {
                msg.append("<li>").append(skipMsg).append("</li>");
            }
            msg.append("</ul></div>");
        }
        return msg.toString();
    }
    @Transactional
    public void deleteUploadedFile(Integer fileId) {
        // 1. Reset Partners (Transactions from OTHER files that were matched to THIS file)
        // We move them back to 'S' (Staged) so they can find new matches in the next run.
        List<Integer> partnerIds = reconciliationMatchedRepository.findPartnerIdsByFileId(fileId);
        if (!partnerIds.isEmpty()) {
            txnRepo.updateStatusByIdsNative(partnerIds, "S");
        }

        // 2. Delete matches where THIS file was either the Payment or Settlement side
        reconciliationMatchedRepository.deleteByFileIdNative(fileId);

        // 3. Delete unmatched records for THIS file
        reconciliationUnmatchedRepository.deleteByFileIdNative(fileId);

        // 4. Delete the actual transactions of THIS file
        txnRepo.deleteByFileIdNative(fileId);
        // Find the fund linked to the file
        Optional<FundEntity> existingFund = fundRepository.findFundByUploadedFileId(fileId);
        if (existingFund.isPresent()) {
            // 5. Delete the actual fund entity found
            fundRepository.delete(existingFund.get());
        }

        // 6. Delete the file record
        uploadedFileRepository.deleteById(fileId);
    }

    private static class FileTask {
        MultipartFile file; SourceType sourceType;
        FileTask(MultipartFile f, SourceType s) { this.file = f; this.sourceType = s; }
    }
    public void deleteMultipleFiles(List<Integer> fileIds) {
        // Fetch the entities first so Hibernate can manage the cascade deletion
        List<UploadedFileEntity> filesToDelete = uploadedFileRepository.findAllById(fileIds);

        if (!filesToDelete.isEmpty()) {
            // This triggers the orphanRemoval and CascadeType.ALL logic
            uploadedFileRepository.deleteAll(filesToDelete);
        }
    }
    public List<UploadedFileEntity> findByStatus(FileStatus fileStatus){
        return uploadedFileRepository.findByStatus(fileStatus);
    }
    public UploadedFileEntity findById(int id){
        return uploadedFileRepository.findById(id);
    }
    public List<UploadedFileEntity> findAll(){
        return uploadedFileRepository.findAll();
    }
    public List<UploadedFileEntity> findByUploadDate(LocalDateTime start, LocalDateTime end){
        return uploadedFileRepository.findByUploadTimeBetween(start, end);
    }
    public void deleteAllInBatch(){
        // 1. Delete transactions first (they are the 'stage' data linked to files)
        txnRepo.deleteAllInBatch();
        reconciliationUnmatchedRepository.deleteAllInBatch();
        reconciliationMatchedRepository.deleteAllInBatch();
        fundRepository.deleteAllInBatch();
        // 2. Now you can safely delete the file records
        uploadedFileRepository.deleteAllInBatch();
    }
}