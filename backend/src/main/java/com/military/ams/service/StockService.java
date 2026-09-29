package com.military.ams.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.military.ams.entity.EquipmentType;
import com.military.ams.entity.MilitaryBase;
import com.military.ams.entity.StockBalance;
import com.military.ams.exception.BusinessRuleException;
import com.military.ams.repository.StockBalanceRepository;

/**
 * The only place stock quantities are changed. Every method here must run inside
 * an existing transaction ({@link Propagation#MANDATORY}) and takes a row-level
 * write lock on the stock balance before mutating it, which makes concurrent
 * purchases / transfers / assignments / expenditures safe.
 */
@Service
public class StockService {

    private final StockBalanceRepository stockBalanceRepository;

    public StockService(StockBalanceRepository stockBalanceRepository) {
        this.stockBalanceRepository = stockBalanceRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public StockBalance lock(MilitaryBase base, EquipmentType equipmentType) {
        return stockBalanceRepository.findForUpdate(base.getId(), equipmentType.getId())
                .orElseGet(() -> stockBalanceRepository.saveAndFlush(new StockBalance(base, equipmentType)));
    }

    public void increase(StockBalance stock, int quantity) {
        stock.setOnHandQuantity(stock.getOnHandQuantity() + quantity);
        stockBalanceRepository.save(stock);
    }

    public void decrease(StockBalance stock, int quantity) {
        if (stock.getOnHandQuantity() < quantity) {
            throw BusinessRuleException.insufficientStock(
                    stock.getBase().getName(),
                    stock.getEquipmentType().getName(),
                    quantity,
                    stock.getOnHandQuantity());
        }
        stock.setOnHandQuantity(stock.getOnHandQuantity() - quantity);
        stockBalanceRepository.save(stock);
    }

    /** Current on-hand quantity, or zero when the pair has no stock row yet. */
    @Transactional(readOnly = true)
    public int availableQuantity(MilitaryBase base, EquipmentType equipmentType) {
        return stockBalanceRepository.findByBaseIdAndEquipmentTypeId(base.getId(), equipmentType.getId())
                .map(StockBalance::getOnHandQuantity)
                .orElse(0);
    }
}
