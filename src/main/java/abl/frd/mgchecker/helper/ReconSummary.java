package abl.frd.mgchecker.helper;

import java.math.BigDecimal;

public class ReconSummary {
    private String date;
    private long payCount;
    private BigDecimal payAmount;
    private long setCount;
    private BigDecimal setAmount;
    private long txnDiff;
    private BigDecimal amtDiff;

    public ReconSummary(String date, long payCount, BigDecimal payAmount, long setCount, BigDecimal setAmount) {
        this.date = date;
        this.payCount = payCount;
        this.payAmount = payAmount != null ? payAmount : BigDecimal.ZERO;
        this.setCount = setCount;
        this.setAmount = setAmount != null ? setAmount : BigDecimal.ZERO;
        // Calculate differences
        this.txnDiff = Math.abs(this.payCount - this.setCount);
        this.amtDiff = this.payAmount.subtract(this.setAmount).abs();
    }

    // Getters are REQUIRED for Thymeleaf
    public String getDate() { return date; }
    public long getPayCount() { return payCount; }
    public BigDecimal getPayAmount() { return payAmount; }
    public long getSetCount() { return setCount; }
    public BigDecimal getSetAmount() { return setAmount; }
    public long getTxnDiff() { return txnDiff; }
    public BigDecimal getAmtDiff() { return amtDiff; }
}
