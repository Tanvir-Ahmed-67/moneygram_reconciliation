package abl.frd.mgchecker.repository;

import abl.frd.mgchecker.enumpack.FileStatus;
import abl.frd.mgchecker.enumpack.SourceType;
import abl.frd.mgchecker.model.UploadedFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UploadedFileRepository extends JpaRepository<UploadedFileEntity, Integer> {
    UploadedFileEntity findById(int id);
    List<UploadedFileEntity> findByStatus(FileStatus status);
    List<UploadedFileEntity> findByUploadTimeBetween(LocalDateTime start, LocalDateTime end);
    Optional<UploadedFileEntity> findByFileNameAndSourceType(String fileName, SourceType sourceType);
    List<UploadedFileEntity> findBySourceType(SourceType sourceType);

    List<UploadedFileEntity> findByStatus(String type);
    @Query("SELECT f FROM UploadedFileEntity f WHERE f.status = 'STAGED' " + "AND NOT EXISTS (SELECT t FROM TransactionEntity t WHERE t.uploadedFile = f AND t.reconStatus = 'S')")
        List<UploadedFileEntity> findFilesReadyToProcess();

    @Query("SELECT f FROM UploadedFileEntity f WHERE f.sourceType = :sourceType AND NOT EXISTS (SELECT fund FROM FundEntity fund WHERE fund.uploadedFile = f)")
    List<UploadedFileEntity> findUnmappedFilesBySourceType(@org.springframework.data.repository.query.Param("sourceType") SourceType sourceType);

    long countBySourceType(SourceType sourceType);
}
