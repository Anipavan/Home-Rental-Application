package com.spa.home_rental_application.document_service.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spa.home_rental_application.document_service.DTO.Response.DocumentResponseDto;
import com.spa.home_rental_application.document_service.DTO.Response.ExtractedDataDto;
import com.spa.home_rental_application.document_service.Entities.Document;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

@Component
@Slf4j
public class DocumentMapper {

    private static final TypeReference<Map<String, String>> MAP_REF = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public DocumentMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public DocumentResponseDto toResponse(Document d) {
        if (d == null) return null;
        return new DocumentResponseDto(
                d.getId(),
                d.getUserId(),
                d.getDocumentType(),
                d.getOriginalFilename(),
                d.getContentType(),
                d.getFileSizeBytes(),
                d.getOcrStatus(),
                parseExtracted(d.getExtractedDataJson()),
                d.getFraudFlag(),
                d.getConfidenceScore(),
                d.getVerifiedBy(),
                d.getVerifiedAt(),
                d.getUploadedAt(),
                d.getUpdatedAt()
        );
    }

    public ExtractedDataDto toExtractedDto(Document d) {
        if (d == null) return null;
        return new ExtractedDataDto(
                d.getId(),
                d.getDocumentType(),
                parseExtracted(d.getExtractedDataJson()),
                d.getFraudFlag(),
                d.getConfidenceScore(),
                d.getOcrStatus(),
                d.getVerifiedAt() != null ? d.getVerifiedAt() : d.getUpdatedAt()
        );
    }

    public String serializeExtracted(Map<String, String> extracted) {
        if (extracted == null || extracted.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(extracted);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize extracted data — storing null", e);
            return null;
        }
    }

    private Map<String, String> parseExtracted(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, MAP_REF);
        } catch (Exception e) {
            log.warn("Failed to deserialize extracted data — returning empty map", e);
            return Collections.emptyMap();
        }
    }
}
