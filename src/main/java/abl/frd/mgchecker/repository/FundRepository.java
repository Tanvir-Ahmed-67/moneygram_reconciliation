package abl.frd.mgchecker.repository;

import abl.frd.mgchecker.model.FundEntity;
import abl.frd.mgchecker.model.UploadedFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.List;

@Repository
public interface FundRepository extends JpaRepository<FundEntity, Integer> {
    UploadedFileEntity findByUploadedFileId(Integer fileId);

    @Transactional
    void deleteByUploadedFileId(Long fileId);

    @Query("SELECT f FROM FundEntity f WHERE f.uploadedFile.id = :fileId")
    Optional<FundEntity> findFundByUploadedFileId(Integer fileId);

    @Query("SELECT f FROM FundEntity f WHERE f.valueDate >= :startDate AND f.valueDate <= :endDate")
    List<FundEntity> findFundsByDateRange(@Param("startDate") String startDate, @Param("endDate") String endDate);
}
