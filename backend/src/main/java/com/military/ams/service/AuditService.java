package com.military.ams.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.military.ams.entity.AuditLog;
import com.military.ams.entity.MilitaryBase;
import com.military.ams.repository.AuditLogRepository;
import com.military.ams.repository.BaseRepository;
import com.military.ams.security.AppUserPrincipal;
import com.military.ams.security.CurrentUserService;

/**
 * Writes the append-only audit trail. Audit entries are committed in their own
 * transaction so that they are preserved even when the business transaction
 * they describe is rolled back (for example a rejected stock movement).
 */
@Service
public class AuditService {

    public static final String ACTION_LOGIN = "LOGIN";
    public static final String ACTION_LOGIN_FAILED = "LOGIN_FAILED";
    public static final String ACTION_LOGOUT = "LOGOUT";
    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_UPDATE = "UPDATE";
    public static final String ACTION_DELETE = "DELETE";
    public static final String ACTION_STATUS_CHANGE = "STATUS_CHANGE";
    public static final String ACTION_PASSWORD_RESET = "PASSWORD_RESET";

    private final AuditLogRepository auditLogRepository;
    private final BaseRepository baseRepository;
    private final CurrentUserService currentUserService;

    public AuditService(AuditLogRepository auditLogRepository,
                        BaseRepository baseRepository,
                        CurrentUserService currentUserService) {
        this.auditLogRepository = auditLogRepository;
        this.baseRepository = baseRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String action, String entity, Long entityId, Long baseId, String description) {
        AppUserPrincipal principal = currentUserService.requireCurrentUser();
        logAs(principal.getId(), principal.getUsername(), action, entity, entityId, baseId, description);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAs(Long userId, String username, String action, String entity, Long entityId, Long baseId,
                      String description) {
        AuditLog auditLog = new AuditLog();
        auditLog.setUserId(userId);
        auditLog.setUsername(username);
        auditLog.setAction(action);
        auditLog.setEntity(entity);
        auditLog.setEntityId(entityId);
        if (baseId != null) {
            auditLog.setBase(baseRepository.findById(baseId).orElse(null));
        }
        auditLog.setDescription(truncate(description));
        auditLog.setCreatedAt(LocalDateTime.now());
        auditLogRepository.save(auditLog);
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    /**
     * Read-only access to the audit trail. Base commanders only ever see entries
     * recorded against their own base.
     */
    @Transactional(readOnly = true)
    public com.military.ams.dto.PageResponse<com.military.ams.dto.AuditLogDto> search(
            String entity, String action, Long userId, Long baseId,
            LocalDateTime from, LocalDateTime to, int page, int size,
            com.military.ams.security.AccessScopeService accessScopeService) {

        Long scopedBaseId = accessScopeService.resolveBaseId(baseId);
        var principal = accessScopeService.currentUser();
        Long scopedUserId = principal.isAdmin() || principal.isLogisticsOfficer() ? userId : principal.getId();

        var result = auditLogRepository.search(
                entity == null || entity.isBlank() ? null : entity.trim(),
                action == null || action.isBlank() ? null : action.trim(),
                scopedUserId,
                scopedBaseId,
                from,
                to,
                org.springframework.data.domain.PageRequest.of(page, size));

        return com.military.ams.dto.PageResponse.of(
                result.getContent().stream().map(auditLog -> new com.military.ams.dto.AuditLogDto(
                        auditLog.getId(),
                        auditLog.getUserId(),
                        auditLog.getUsername(),
                        auditLog.getAction(),
                        auditLog.getEntity(),
                        auditLog.getEntityId(),
                        auditLog.getBase() == null ? null : auditLog.getBase().getId(),
                        auditLog.getBase() == null ? null : auditLog.getBase().getCode(),
                        auditLog.getDescription(),
                        auditLog.getCreatedAt())).toList(),
                page, size, result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public List<String> knownEntities() {
        return auditLogRepository.findDistinctEntities();
    }
}
