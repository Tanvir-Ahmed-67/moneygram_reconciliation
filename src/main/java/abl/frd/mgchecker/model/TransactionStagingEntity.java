package abl.frd.mgchecker.model;

import abl.frd.mgchecker.enumpack.ReconStatus;
import abl.frd.mgchecker.enumpack.SourceType;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Entity
public class TransactionStagingEntity {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private int  id;
    @Column(name = "transaction_no", length=30, nullable = false)
    private String transactionNo;

    @Column(name = "reference_no", length=30, nullable = false)
    private String referenceNo;
    @Column(name = "org_country", length=30, nullable = false)
    private String originatingCountry;

    @Column(name = "amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal amount;
    @Column(name = "transaction_date", length=30)
    private String transactionDate;

    @Column(name = "file_upload_date", length=30)
    private LocalDateTime fileUploadDate;

    @Column(name = "legacy_id", length=30)
    private String legacyId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SourceType sourceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReconStatus reconStatus = ReconStatus.S;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private UploadedFileEntity uploadedFile;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTransactionNo() {
        return transactionNo;
    }

    public void setTransactionNo(String transactionNo) {
        this.transactionNo = transactionNo;
    }

    public String getReferenceNo() {
        return referenceNo;
    }

    public void setReferenceNo(String referenceNo) {
        this.referenceNo = referenceNo;
    }

    public String getOriginatingCountry() {
        return originatingCountry;
    }

    public void setOriginatingCountry(String originatingCountry) {
        this.originatingCountry = originatingCountry;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(String transactionDate) {
        this.transactionDate = transactionDate;
    }

    public LocalDateTime getFileUploadDate() {
        return fileUploadDate;
    }

    public void setFileUploadDate(LocalDateTime fileUploadDate) {
        this.fileUploadDate = fileUploadDate;
    }

    public String getLegacyId() {
        return legacyId;
    }

    public void setLegacyId(String legacyId) {
        this.legacyId = legacyId;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(SourceType sourceType) {
        this.sourceType = sourceType;
    }

    public ReconStatus getReconStatus() {
        return reconStatus;
    }

    public void setReconStatus(ReconStatus reconStatus) {
        this.reconStatus = reconStatus;
    }

    public UploadedFileEntity getUploadedFile() {
        return uploadedFile;
    }

    public void setUploadedFile(UploadedFileEntity uploadedFile) {
        this.uploadedFile = uploadedFile;
    }
}
