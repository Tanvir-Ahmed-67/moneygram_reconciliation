package abl.frd.mgchecker.model;

import abl.frd.mgchecker.enumpack.MatchType;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "reconciliation_match",
        uniqueConstraints = {
                @UniqueConstraint(
                        columnNames = {"payment_transaction_no", "settlement_transaction_no"}
                )
        }
)
public class ReconciliationMatch {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private int  id;
    @Column(name = "payment_transaction_no", length=30, nullable = false)
    private String paymentTransactionNo;
    @Column(name = "settlement_transaction_no", length=30, nullable = false)
    private String settlementTransactionNo;

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

    public String getPaymentTransactionNo() {
        return paymentTransactionNo;
    }

    public void setPaymentTransactionNo(String paymentTransactionNo) {
        this.paymentTransactionNo = paymentTransactionNo;
    }

    public String getSettlementTransactionNo() {
        return settlementTransactionNo;
    }

    public void setSettlementTransactionNo(String settlementTransactionNo) {
        this.settlementTransactionNo = settlementTransactionNo;
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
}
