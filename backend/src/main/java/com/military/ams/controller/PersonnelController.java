package com.military.ams.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.military.ams.dto.PageResponse;
import com.military.ams.dto.PersonnelDto;
import com.military.ams.dto.PersonnelRequest;
import com.military.ams.service.PersonnelService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/personnel")
@PreAuthorize("hasAnyRole('ADMIN','BASE_COMMANDER')")
public class PersonnelController {

    private final PersonnelService personnelService;

    public PersonnelController(PersonnelService personnelService) {
        this.personnelService = personnelService;
    }

    @GetMapping
    public List<PersonnelDto> list(@RequestParam(required = false) Long baseId) {
        return personnelService.findByBase(baseId);
    }

    @GetMapping("/search")
    public PageResponse<PersonnelDto> search(@RequestParam(required = false) Long baseId,
                                             @RequestParam(required = false) String q,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return personnelService.search(baseId, q, page, size);
    }

    @PostMapping
    public ResponseEntity<PersonnelDto> create(@Valid @RequestBody PersonnelRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(personnelService.create(request));
    }

    @PutMapping("/{id}")
    public PersonnelDto update(@PathVariable Long id, @Valid @RequestBody PersonnelRequest request) {
        return personnelService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        personnelService.delete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
