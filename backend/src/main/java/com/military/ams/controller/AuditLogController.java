package com.military.ams.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.military.ams.dto.AuditLogDto;
import com.military.ams.dto.PageResponse;
import com.military.ams.security.AccessScopeService;
import com.military.ams.service.AuditService;

/**
 * Read-only view over the audit trail. There is deliberately no PUT, PATCH or
 * DELETE endpoint here, and none may be added without a data-retention review.
 */
@RestController
@RequestMapping("/api/audit-logs")
@PreAuthorize("hasAnyRole('ADMIN','BASE_COMMANDER','LOGISTICS_OFFICER')")
public class AuditLogController {

    private final AuditService auditService;
    private final AccessScopeService accessScopeService;

    public AuditLogController(AuditService auditService, AccessScopeService accessScopeService) {
        this.auditService = auditService;
        this.accessScopeService = accessScopeService;
    }

    @GetMapping
    public PageResponse<AuditLogDto> list(
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long baseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return auditService.search(entity, action, userId, baseId, from, to, page, size, accessScopeService);
    }

    @GetMapping("/entities")
    public List<String> entities() {
        return auditService.knownEntities();
    }
}
