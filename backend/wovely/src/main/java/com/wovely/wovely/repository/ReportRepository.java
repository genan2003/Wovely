package com.wovely.wovely.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import com.wovely.wovely.models.Report;
import java.util.List;

public interface ReportRepository extends MongoRepository<Report, String> {
    List<Report> findByStatus(String status);
}
