package com.softropic.sendam.security.audit.api;



import com.softropic.sendam.security.audit.repository.AuditLog;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING) //(componentModel = "spring")
public interface AuditTrailMapper {
    AuditTrail toAuditTrail(AuditLog auditLog);
    AuditLog toAuditLog(AuditTrail auditTrail);
}
