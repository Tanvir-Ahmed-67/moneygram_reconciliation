package abl.frd.mgchecker.repository;

import abl.frd.mgchecker.enumpack.FileStatus;
import abl.frd.mgchecker.enumpack.SourceType;
import abl.frd.mgchecker.model.UploadedFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UploadedFileRepository extends JpaRepository<UploadedFileEntity, Integer> {
    Optional<UploadedFileEntity> findById(Long id);
    List<UploadedFileEntity> findByStatus(FileStatus status);
    Optional<UploadedFileEntity> findByFileNameAndSourceType(String fileName, SourceType sourceType);

    List<UploadedFileEntity> findByStatus(String type);
}
