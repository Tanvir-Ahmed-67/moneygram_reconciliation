package abl.frd.mgchecker.model;

import abl.frd.mgchecker.enumpack.MatchType;
import abl.frd.mgchecker.enumpack.SourceType;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "reconciliation_match",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_match_ids", columnNames = {"payment_id", "settlement_id"})
        }
)
public class ReconciliationMatch {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private int  id;

    @Column(name = "payment_id")
    private int paymentId; // The ID from the 'transactions' table

    @Column(name = "settlement_id")
    private int settlementId; // The ID from the 'transactions' table
    @Column(name = "payment_file_id")
    private Integer paymentFileId;

    @Column(name = "settlement_file_id")
    private Integer settlementFileId;
    @Enumerated(EnumType.STRING)
    private SourceType sourceType;
    private String transactionNo;
    private String referenceNo;
    private String orgCountry;
    private BigDecimal amount;
    private String transactionDate;
    private String legacyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchType matchType;
    @Column(nullable = false)
    private LocalDateTime matchedOn;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(SourceType sourceType) {
        this.sourceType = sourceType;
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

    public String getOrgCountry() {
        return orgCountry;
    }

    public void setOrgCountry(String orgCountry) {
        this.orgCountry = orgCountry;
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

    public String getLegacyId() {
        return legacyId;
    }

    public void setLegacyId(String legacyId) {
        this.legacyId = legacyId;
    }

    public MatchType getMatchType() {
        return matchType;
    }

    public void setMatchType(MatchType matchType) {
        this.matchType = matchType;
    }

    public LocalDateTime getMatchedOn() {
        return matchedOn;
    }

    public void setMatchedOn(LocalDateTime matchedOn) {
        this.matchedOn = matchedOn;
    }

    public Integer getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(Integer paymentId) {
        this.paymentId = paymentId;
    }

    public Integer getSettlementId() {
        return settlementId;
    }

    public void setSettlementId(Integer settlementId) {
        this.settlementId = settlementId;
    }

    public void setPaymentId(int paymentId) {
        this.paymentId = paymentId;
    }

    public void setSettlementId(int settlementId) {
        this.settlementId = settlementId;
    }

    public Integer getPaymentFileId() {
        return paymentFileId;
    }

    public void setPaymentFileId(Integer paymentFileId) {
        this.paymentFileId = paymentFileId;
    }

    public Integer getSettlementFileId() {
        return settlementFileId;
    }

    public void setSettlementFileId(Integer settlementFileId) {
        this.settlementFileId = settlementFileId;
    }
}
