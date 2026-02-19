package abl.frd.mgchecker.helper;

import java.math.BigDecimal;

public class FileSummary {
    private int totalCount;
    private BigDecimal totalAmount;

    public FileSummary(int totalCount, BigDecimal totalAmount) {
        this.totalCount = totalCount;
        this.totalAmount = totalAmount;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }
}

