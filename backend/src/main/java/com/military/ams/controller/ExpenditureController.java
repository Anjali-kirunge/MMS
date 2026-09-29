package com.military.ams.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.military.ams.dto.ExpenditureDto;
import com.military.ams.dto.ExpenditureRequest;
import com.military.ams.dto.PageResponse;
import com.military.ams.service.ExpenditureService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/expenditures")
@PreAuthorize("hasAnyRole('ADMIN','BASE_COMMANDER')")
public class ExpenditureController {

    private final ExpenditureService expenditureService;

    public ExpenditureController(ExpenditureService expenditureService) {
        this.expenditureService = expenditureService;
    }

    @GetMapping
    public PageResponse<ExpenditureDto> list(
            @RequestParam(required = false) Long baseId,
            @RequestParam(required = false) Long equipmentTypeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return expenditureService.search(baseId, equipmentTypeId, from, to, page, size);
    }

    @GetMapping("/{id}")
    public ExpenditureDto get(@PathVariable Long id) {
        return expenditureService.findById(id);
    }

    @PostMapping
    public ResponseEntity<ExpenditureDto> create(@Valid @RequestBody ExpenditureRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(expenditureService.create(request));
    }
}
