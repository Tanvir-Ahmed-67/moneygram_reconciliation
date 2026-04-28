package abl.frd.mgchecker.repository;

import abl.frd.mgchecker.model.FundEntity;
import abl.frd.mgchecker.model.UploadedFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface FundRepository extends JpaRepository<FundEntity, Integer> {
    UploadedFileEntity findByUploadedFileId(Integer fileId);
    @Transactional
    void deleteByUploadedFileId(Long fileId);

    @Query("SELECT f FROM FundEntity f WHERE f.uploadedFile.id = :fileId")
    Optional<FundEntity> findFundByUploadedFileId(Integer fileId);
}
