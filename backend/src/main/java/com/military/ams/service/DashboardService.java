package com.military.ams.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.military.ams.dto.DashboardDto;
import com.military.ams.dto.DashboardRowDto;
import com.military.ams.dto.DashboardTotalsDto;
import com.military.ams.dto.MovementAggregate;
import com.military.ams.dto.MovementLineDto;
import com.military.ams.entity.EquipmentType;
import com.military.ams.entity.MilitaryBase;
import com.military.ams.entity.PurchaseItem;
import com.military.ams.entity.StockBalance;
import com.military.ams.entity.TransferItem;
import com.military.ams.exception.BusinessRuleException;
import com.military.ams.repository.BaseRepository;
import com.military.ams.repository.EquipmentTypeRepository;
import com.military.ams.repository.PurchaseRepository;
import com.military.ams.repository.StockBalanceRepository;
import com.military.ams.repository.TransferRepository;
import com.military.ams.security.AccessScopeService;

/**
 * Dashboard figures.
 *
 * <pre>
 *   Net Movement  = Purchases + Transfer In - Transfer Out
 *   Closing       = Opening + Purchases + Transfer In - Transfer Out - Assigned - Expended
 * </pre>
 *
 * When a start date is supplied, "Opening" is the balance carried into that
 * period, i.e. the seeded opening balance plus every movement dated before it.
 */
@Service
public class DashboardService {

    public static final String MOVEMENT_PURCHASE = "PURCHASE";
    public static final String MOVEMENT_TRANSFER_IN = "TRANSFER_IN";
    public static final String MOVEMENT_TRANSFER_OUT = "TRANSFER_OUT";

    private static final LocalDate MIN_DATE = LocalDate.of(1970, 1, 1);
    private static final LocalDate MAX_DATE = LocalDate.of(2999, 12, 31);

    private final StockBalanceRepository stockBalanceRepository;
    private final PurchaseRepository purchaseRepository;
    private final TransferRepository transferRepository;
    private final com.military.ams.repository.AssignmentRepository assignmentRepository;
    private final com.military.ams.repository.ExpenditureRepository expenditureRepository;
    private final BaseRepository baseRepository;
    private final EquipmentTypeRepository equipmentTypeRepository;
    private final AccessScopeService accessScopeService;

    public DashboardService(StockBalanceRepository stockBalanceRepository,
                            PurchaseRepository purchaseRepository,
                            TransferRepository transferRepository,
                            com.military.ams.repository.AssignmentRepository assignmentRepository,
                            com.military.ams.repository.ExpenditureRepository expenditureRepository,
                            BaseRepository baseRepository,
                            EquipmentTypeRepository equipmentTypeRepository,
                            AccessScopeService accessScopeService) {
        this.stockBalanceRepository = stockBalanceRepository;
        this.purchaseRepository = purchaseRepository;
        this.transferRepository = transferRepository;
        this.assignmentRepository = assignmentRepository;
        this.expenditureRepository = expenditureRepository;
        this.baseRepository = baseRepository;
        this.equipmentTypeRepository = equipmentTypeRepository;
        this.accessScopeService = accessScopeService;
    }

    @Transactional(readOnly = true)
    public DashboardDto summary(Long baseId, Long equipmentTypeId, LocalDate from, LocalDate to) {
        Long scopedBaseId = accessScopeService.resolveBaseId(baseId);
        LocalDate effectiveFrom = from == null ? MIN_DATE : from;
        LocalDate effectiveTo = to == null ? MAX_DATE : to;
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new BusinessRuleException("The 'from' date must not be after the 'to' date");
        }

        Map<Key, Long> openingSeed = toMap(stockBalanceRepository
                .search(scopedBaseId, equipmentTypeId, org.springframework.data.domain.Pageable.unpaged())
                .getContent().stream()
                .map(stock -> new StockBalanceSeed(stock.getBase().getId(), stock.getEquipmentType().getId(),
                        stock.getOpeningBalance().longValue()))
                .toList());

        Map<Key, Long> beforePurchases = index(purchaseRepository.aggregateBefore(scopedBaseId, equipmentTypeId, effectiveFrom));
        Map<Key, Long> beforeTransferIn = index(transferRepository.aggregateTransferInBefore(scopedBaseId, equipmentTypeId, effectiveFrom));
        Map<Key, Long> beforeTransferOut = index(transferRepository.aggregateTransferOutBefore(scopedBaseId, equipmentTypeId, effectiveFrom));
        Map<Key, Long> beforeAssigned = index(assignmentRepository.aggregateBefore(scopedBaseId, equipmentTypeId, effectiveFrom));
        Map<Key, Long> beforeExpended = index(expenditureRepository.aggregateBefore(scopedBaseId, equipmentTypeId, effectiveFrom));

        Map<Key, Long> purchases = index(purchaseRepository.aggregateBetween(scopedBaseId, equipmentTypeId, effectiveFrom, effectiveTo));
        Map<Key, Long> transferIn = index(transferRepository.aggregateTransferInBetween(scopedBaseId, equipmentTypeId, effectiveFrom, effectiveTo));
        Map<Key, Long> transferOut = index(transferRepository.aggregateTransferOutBetween(scopedBaseId, equipmentTypeId, effectiveFrom, effectiveTo));
        Map<Key, Long> assigned = index(assignmentRepository.aggregateBetween(scopedBaseId, equipmentTypeId, effectiveFrom, effectiveTo));
        Map<Key, Long> expended = index(expenditureRepository.aggregateBetween(scopedBaseId, equipmentTypeId, effectiveFrom, effectiveTo));

        Set<Key> keys = new HashSet<>();
        keys.addAll(openingSeed.keySet());
        keys.addAll(purchases.keySet());
        keys.addAll(transferIn.keySet());
        keys.addAll(transferOut.keySet());
        keys.addAll(assigned.keySet());
        keys.addAll(expended.keySet());

        Map<Long, MilitaryBase> bases = baseRepository.findAll().stream()
                .collect(Collectors.toMap(MilitaryBase::getId, Function.identity()));
        Map<Long, EquipmentType> equipment = equipmentTypeRepository.findAll().stream()
                .collect(Collectors.toMap(EquipmentType::getId, Function.identity()));

        List<Key> ordered = new ArrayList<>(keys);
        ordered.sort(Comparator
                .comparing((Key key) -> bases.containsKey(key.baseId()) ? bases.get(key.baseId()).getName() : "")
                .thenComparing(key -> equipment.containsKey(key.equipmentTypeId())
                        ? equipment.get(key.equipmentTypeId()).getName() : ""));

        List<DashboardRowDto> rows = new ArrayList<>();
        long totalOpening = 0;
        long totalPurchases = 0;
        long totalTransferIn = 0;
        long totalTransferOut = 0;
        long totalAssigned = 0;
        long totalExpended = 0;

        for (Key key : ordered) {
            MilitaryBase base = bases.get(key.baseId());
            EquipmentType type = equipment.get(key.equipmentTypeId());
            if (base == null || type == null) {
                continue;
            }

            long opening = get(openingSeed, key)
                    + get(beforePurchases, key)
                    + get(beforeTransferIn, key)
                    - get(beforeTransferOut, key)
                    - get(beforeAssigned, key)
                    - get(beforeExpended, key);
            long periodPurchases = get(purchases, key);
            long periodTransferIn = get(transferIn, key);
            long periodTransferOut = get(transferOut, key);
            long periodAssigned = get(assigned, key);
            long periodExpended = get(expended, key);
            long netMovement = periodPurchases + periodTransferIn - periodTransferOut;
            long closing = opening + periodPurchases + periodTransferIn - periodTransferOut
                    - periodAssigned - periodExpended;

            rows.add(new DashboardRowDto(
                    base.getId(), base.getCode(), base.getName(),
                    type.getId(), type.getCode(), type.getName(), type.getUnit(),
                    opening, periodPurchases, periodTransferIn, periodTransferOut, netMovement,
                    periodAssigned, periodExpended, closing));

            totalOpening += opening;
            totalPurchases += periodPurchases;
            totalTransferIn += periodTransferIn;
            totalTransferOut += periodTransferOut;
            totalAssigned += periodAssigned;
            totalExpended += periodExpended;
        }

        long totalNetMovement = totalPurchases + totalTransferIn - totalTransferOut;
        long totalClosing = totalOpening + totalPurchases + totalTransferIn - totalTransferOut
                - totalAssigned - totalExpended;

        DashboardTotalsDto totals = new DashboardTotalsDto(
                totalOpening, totalPurchases, totalTransferIn, totalTransferOut,
                totalNetMovement, totalAssigned, totalExpended, totalClosing);

        return new DashboardDto(effectiveFrom, effectiveTo, scopedBaseId, equipmentTypeId, totals, rows);
    }

    @Transactional(readOnly = true)
    public List<MovementLineDto> movements(String movementType, Long baseId, Long equipmentTypeId,
                                           LocalDate from, LocalDate to) {
        Long scopedBaseId = accessScopeService.resolveBaseId(baseId);
        LocalDate effectiveFrom = from == null ? MIN_DATE : from;
        LocalDate effectiveTo = to == null ? MAX_DATE : to;
        String type = movementType == null ? "" : movementType.trim().toUpperCase();

        switch (type) {
            case MOVEMENT_PURCHASE -> {
                if (scopedBaseId == null) {
                    throw new BusinessRuleException("A base filter is required to view purchase movements");
                }
                return purchaseRepository.findItemsBetween(scopedBaseId, equipmentTypeId, effectiveFrom, effectiveTo)
                        .stream().map(item -> toLine(item, MOVEMENT_PURCHASE, scopedBaseId)).toList();
            }
            case MOVEMENT_TRANSFER_IN -> {
                if (scopedBaseId == null) {
                    throw new BusinessRuleException("A base filter is required to view transfer-in movements");
                }
                return transferRepository.findItemsReceivedBy(scopedBaseId, equipmentTypeId, effectiveFrom, effectiveTo)
                        .stream().map(item -> toLine(item, MOVEMENT_TRANSFER_IN, scopedBaseId)).toList();
            }
            case MOVEMENT_TRANSFER_OUT -> {
                if (scopedBaseId == null) {
                    throw new BusinessRuleException("A base filter is required to view transfer-out movements");
                }
                return transferRepository.findItemsSentBy(scopedBaseId, equipmentTypeId, effectiveFrom, effectiveTo)
                        .stream().map(item -> toLine(item, MOVEMENT_TRANSFER_OUT, scopedBaseId)).toList();
            }
            default -> throw new BusinessRuleException(
                    "Unknown movement type '" + movementType + "'. Use PURCHASE, TRANSFER_IN or TRANSFER_OUT.");
        }
    }

    private MovementLineDto toLine(PurchaseItem item, String movementType, Long baseId) {
        return new MovementLineDto(
                item.getId(),
                movementType,
                item.getPurchase().getReferenceNo(),
                item.getPurchase().getPurchaseDate(),
                item.getPurchase().getBase().getCode(),
                item.getPurchase().getBase().getCode(),
                item.getEquipmentType().getCode(),
                item.getEquipmentType().getName(),
                item.getEquipmentType().getUnit(),
                item.getQuantity());
    }

    private MovementLineDto toLine(TransferItem item, String movementType, Long baseId) {
        boolean incoming = MOVEMENT_TRANSFER_IN.equals(movementType);
        return new MovementLineDto(
                item.getId(),
                movementType,
                item.getTransfer().getReferenceNo(),
                item.getTransfer().getTransferDate(),
                item.getTransfer().getSourceBase().getCode(),
                item.getTransfer().getDestinationBase().getCode(),
                item.getEquipmentType().getCode(),
                item.getEquipmentType().getName(),
                item.getEquipmentType().getUnit(),
                item.getQuantity());
    }

    private Map<Key, Long> toMap(List<StockBalanceSeed> seeds) {
        Map<Key, Long> map = new HashMap<>();
        for (StockBalanceSeed seed : seeds) {
            map.put(new Key(seed.baseId(), seed.equipmentTypeId()), seed.value());
        }
        return map;
    }

    private Map<Key, Long> index(List<MovementAggregate> aggregates) {
        Map<Key, Long> map = new HashMap<>();
        for (MovementAggregate aggregate : aggregates) {
            map.put(new Key(aggregate.baseId(), aggregate.equipmentTypeId()), aggregate.quantity());
        }
        return map;
    }

    private long get(Map<Key, Long> map, Key key) {
        return map.getOrDefault(key, 0L);
    }

    private record StockBalanceSeed(Long baseId, Long equipmentTypeId, long value) {
    }

    private record Key(Long baseId, Long equipmentTypeId) {
    }
}
