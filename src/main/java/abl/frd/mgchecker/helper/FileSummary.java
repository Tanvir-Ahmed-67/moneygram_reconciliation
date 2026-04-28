package abl.frd.mgchecker.helper;

import java.math.BigDecimal;
import java.time.LocalDate;

public class FileSummary {
    private int totalCount;
    private BigDecimal totalAmount;
    private BigDecimal totalAmountUsd;

    private LocalDate valueDate;

    public FileSummary(int totalCount, BigDecimal totalAmount, LocalDate valueDate, BigDecimal totalAmountUsd) {
        this.totalCount = totalCount;
        this.totalAmount = totalAmount;
        this.valueDate = valueDate;
        this.totalAmountUsd = totalAmountUsd;
    }

    public LocalDate getValueDate() {
        return valueDate;
    }

    public void setValueDate(LocalDate valueDate) {
        this.valueDate = valueDate;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public BigDecimal getTotalAmountUsd() {
        return totalAmountUsd;
    }

    public void setTotalAmountUsd(BigDecimal totalAmountUsd) {
        this.totalAmountUsd = totalAmountUsd;
    }
}

