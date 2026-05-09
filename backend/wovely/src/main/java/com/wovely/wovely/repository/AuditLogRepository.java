package com.wovely.wovely.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import com.wovely.wovely.models.AuditLog;
import java.util.List;

public interface AuditLogRepository extends MongoRepository<AuditLog, String> {
    List<AuditLog> findAllByOrderByTimestampDesc();
}
