package abl.frd.mgchecker.model;

import abl.frd.mgchecker.enumpack.FileStatus;
import abl.frd.mgchecker.enumpack.SourceType;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "uploaded_file")
public class UploadedFileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "source_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private SourceType sourceType;

    @Column(name = "upload_time", nullable = false)
    private LocalDateTime uploadTime;

    @Column(name = "value_date")
    private LocalDate valueDate;

    public LocalDate getValueDate() {
        return valueDate;
    }

    public void setValueDate(LocalDate valueDate) {
        this.valueDate = valueDate;
    }

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private FileStatus status;

    @OneToMany(mappedBy = "uploadedFile", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TransactionEntity> transactions;
    private Integer totalTransactions = 0;

    @Column(precision = 18, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;
    @Column(precision = 18, scale = 2)
    private BigDecimal totalAmountUsd = BigDecimal.ZERO;

    public BigDecimal getTotalAmountUsd() {
        return totalAmountUsd;
    }

    public void setTotalAmountUsd(BigDecimal totalAmountUsd) {
        this.totalAmountUsd = totalAmountUsd;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(SourceType sourceType) {
        this.sourceType = sourceType;
    }

    public LocalDateTime getUploadTime() {
        return uploadTime;
    }

    public void setUploadTime(LocalDateTime uploadTime) {
        this.uploadTime = uploadTime;
    }

    public FileStatus getStatus() {
        return status;
    }

    public void setStatus(FileStatus status) {
        this.status = status;
    }

    public Integer getTotalTransactions() {
        return totalTransactions;
    }

    public void setTotalTransactions(Integer totalTransactions) {
        this.totalTransactions = totalTransactions;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public List<TransactionEntity> getTransactions() {
        return transactions;
    }

    public void setTransactions(List<TransactionEntity> transactions) {
        this.transactions = transactions;
    }
    public String getProcessingStatus() {
        if (transactions == null || transactions.isEmpty()) return "EMPTY";

        long pending = transactions.stream()
                .filter(t -> t.getReconStatus().equals("S"))
                .count();

        return pending == 0 ? "COMPLETED" : "PENDING";
    }
}
