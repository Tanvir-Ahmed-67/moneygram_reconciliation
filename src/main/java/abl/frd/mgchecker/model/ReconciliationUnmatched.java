package abl.frd.mgchecker.model;

import abl.frd.mgchecker.enumpack.SourceType;
import abl.frd.mgchecker.enumpack.UnmatchReason;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "reconciliation_unmatched",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"transaction_no", "source_type"})
        },
        indexes = {
                @Index(
                        name = "idx_unmatched_txn_source",
                        columnList = "transaction_no, source_type"
                )
        }
)
public class ReconciliationUnmatched {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private int  id;
    @Column(name = "transaction_no", length=30, nullable = false)
    private String transactionNo;
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type",nullable = false)
    private SourceType sourceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason",nullable = false)
    private UnmatchReason reason;

    @Column(nullable = false)
    private LocalDateTime detectedOn;

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

    public SourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(SourceType sourceType) {
        this.sourceType = sourceType;
    }

    public UnmatchReason getReason() {
        return reason;
    }

    public void setReason(UnmatchReason reason) {
        this.reason = reason;
    }

    public LocalDateTime getDetectedOn() {
        return detectedOn;
    }

    public void setDetectedOn(LocalDateTime detectedOn) {
        this.detectedOn = detectedOn;
    }
}
