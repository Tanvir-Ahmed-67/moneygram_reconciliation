package abl.frd.mgchecker.model;

import javax.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "fund")
public class FundEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "value_date", nullable = false)
    private String valueDate;

    @Column(name = "reference_no", nullable = false)
    private String referenceNo;

    @Column(name = "amount_usd", precision = 18, scale = 2, nullable = false)
    private BigDecimal amountUsd;

    @Column(name = "conversion_rate", precision = 18, scale = 4, nullable = false)
    private BigDecimal conversionRate;

    @Column(name = "amount_bdt", precision = 18, scale = 2, nullable = false)
    private BigDecimal amountBdt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_file_id")
    private UploadedFileEntity uploadedFile;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getValueDate() {
        return valueDate;
    }

    public void setValueDate(String valueDate) {
        this.valueDate = valueDate;
    }

    public String getReferenceNo() {
        return referenceNo;
    }

    public void setReferenceNo(String referenceNo) {
        this.referenceNo = referenceNo;
    }

    public BigDecimal getAmountUsd() {
        return amountUsd;
    }

    public void setAmountUsd(BigDecimal amountUsd) {
        this.amountUsd = amountUsd;
    }

    public BigDecimal getConversionRate() {
        return conversionRate;
    }

    public void setConversionRate(BigDecimal conversionRate) {
        this.conversionRate = conversionRate;
    }

    public BigDecimal getAmountBdt() {
        return amountBdt;
    }

    public void setAmountBdt(BigDecimal amountBdt) {
        this.amountBdt = amountBdt;
    }

    public UploadedFileEntity getUploadedFile() {
        return uploadedFile;
    }

    public void setUploadedFile(UploadedFileEntity uploadedFile) {
        this.uploadedFile = uploadedFile;
    }
}
