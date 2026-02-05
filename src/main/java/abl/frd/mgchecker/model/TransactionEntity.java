package abl.frd.mgchecker.model;

import abl.frd.mgchecker.enumpack.ReconStatus;
import abl.frd.mgchecker.enumpack.SourceType;

import javax.persistence.*;

@Entity
@Table(
        name = "transaction",
        indexes = {
                @Index(name = "idx_recon_key", columnList = "transaction_no,account_no,amount"),
                @Index(name = "idx_status", columnList = "reconStatus"),
                @Index(name = "idx_source", columnList = "sourceType")
        }
)
public class TransactionEntity {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private int  id;
    @Column(name = "transaction_no", length=30, nullable = false)
    private String transactionNo;
    @Column(name = "currency", length=32)
    private String currency;
    @Column(name = "account_no", length=32)
    private String accountNo;
    @Column(name = "amount", length = 15, nullable = false)
    private Double amount;
    @Column(name = "transaction_date", length=30)
    private String transactionDate;
    @Column(name = "file_upload_date", length=30)
    private String fileUploadDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SourceType sourceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReconStatus reconStatus = ReconStatus.N;

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

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getAccountNo() {
        return accountNo;
    }

    public void setAccountNo(String accountNo) {
        this.accountNo = accountNo;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public String getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(String transactionDate) {
        this.transactionDate = transactionDate;
    }

    public String getFileUploadDate() {
        return fileUploadDate;
    }

    public void setFileUploadDate(String fileUploadDate) {
        this.fileUploadDate = fileUploadDate;
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
}
