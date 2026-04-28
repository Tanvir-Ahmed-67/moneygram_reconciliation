package abl.frd.mgchecker.service;

import abl.frd.mgchecker.model.FundEntity;
import abl.frd.mgchecker.model.UploadedFileEntity;
import abl.frd.mgchecker.repository.FundRepository;
import abl.frd.mgchecker.repository.TransactionRepository;
import abl.frd.mgchecker.repository.UploadedFileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.math.BigDecimal;

@Service
public class FundService {

    private final FundRepository fundRepository;
    private final TransactionRepository transactionRepository;
    private final UploadedFileRepository uploadedFileRepository;

    @Autowired
    public FundService(FundRepository fundRepository,
            TransactionRepository transactionRepository,
            UploadedFileRepository uploadedFileRepository) {
        this.fundRepository = fundRepository;
        this.transactionRepository = transactionRepository;
        this.uploadedFileRepository = uploadedFileRepository;
    }

    @Transactional
    public void saveFund(FundEntity fund) {
        // Find the corresponding Settlement file ID based on the Value Date
        Integer fileId = transactionRepository.findSettlementFileIdByDate(fund.getValueDate());

        if (fileId != null) {
            // Fetch the actual UploadedFileEntity using the file ID
            UploadedFileEntity settlementFile = uploadedFileRepository.findById(fileId).orElse(null);
            fund.setUploadedFile(settlementFile);
        }

        fundRepository.save(fund);
    }

    public List<FundEntity> getAllFunds() {
        return fundRepository.findAll();
    }

    public FundEntity getFundById(Integer id) {
        return fundRepository.findById(id).orElse(null);
    }

    @Transactional
    public void deleteFund(Integer id) {
        fundRepository.deleteById(id);
    }

    @Transactional
    public void updateFund(Integer id, String valueDate, String referenceNo, BigDecimal amountUsd,
            BigDecimal conversionRate, BigDecimal amountBdt) {
        FundEntity fund = fundRepository.findById(id).orElse(null);
        if (fund != null) {
            fund.setValueDate(valueDate);
            fund.setReferenceNo(referenceNo);
            fund.setAmountUsd(amountUsd);
            fund.setConversionRate(conversionRate);
            fund.setAmountBdt(amountBdt);

            // Re-map the Settlement file ID based on the Value Date
            Integer fileId = transactionRepository.findSettlementFileIdByDate(valueDate);
            if (fileId != null) {
                UploadedFileEntity settlementFile = uploadedFileRepository.findById(fileId).orElse(null);
                fund.setUploadedFile(settlementFile);
            } else {
                fund.setUploadedFile(null);
            }

            fundRepository.save(fund);
        }
    }

}