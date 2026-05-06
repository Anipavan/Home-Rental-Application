package com.spa.home_rental_application.user_service.user_service.service.impul;

import com.spa.home_rental_application.user_service.user_service.DTO.Request.EmergencyContactRequestDto;
import com.spa.home_rental_application.user_service.user_service.DTO.Response.EmergencyContactResponseDto;
import com.spa.home_rental_application.user_service.user_service.Entities.EmergencyContact;
import com.spa.home_rental_application.user_service.user_service.Exceptionclass.RecordNotFound;
import com.spa.home_rental_application.user_service.user_service.mapper.EmergencyContactMapper;
import com.spa.home_rental_application.user_service.user_service.repositry.EmergencyContactRepo;
import com.spa.home_rental_application.user_service.user_service.repositry.UserRepo;
import com.spa.home_rental_application.user_service.user_service.service.EmergencyContactService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class EmergencyContactImpul implements EmergencyContactService {

    private final EmergencyContactRepo emergencyContactRepo;
    private final UserRepo userRepo;

    public EmergencyContactImpul(EmergencyContactRepo emergencyContactRepo, UserRepo userRepo) {
        this.emergencyContactRepo = emergencyContactRepo;
        this.userRepo = userRepo;
    }

    @Override
    @Transactional
    public EmergencyContactResponseDto saveUsersEmergencyContact(EmergencyContactRequestDto dto) {
        // Verify the linked user exists — otherwise we'd create orphan contacts.
        userRepo.findActiveById(dto.userId()).orElseThrow(
                () -> new RecordNotFound("Cannot create emergency contact: user not found id=" + dto.userId()));

        EmergencyContact contact = EmergencyContactMapper.toEntity(dto);
        EmergencyContact saved = emergencyContactRepo.save(contact);
        log.info("Emergency contact created id={} for user={}", saved.getId(), dto.userId());
        return EmergencyContactMapper.toDto(saved);
    }

    @Override
    @Transactional
    public EmergencyContactResponseDto UpdateEmergencyContact(EmergencyContactRequestDto dto, String contactId) {
        EmergencyContact existing = emergencyContactRepo.findById(contactId).orElseThrow(
                () -> new RecordNotFound("Emergency contact not found with id: " + contactId));

        if (notBlank(dto.name()))     existing.setName(dto.name());
        if (notBlank(dto.phone()))    existing.setPhone(dto.phone());
        if (notBlank(dto.relation())) existing.setRelation(dto.relation());
        if (notBlank(dto.userId()))   existing.setUserId(dto.userId());
        existing.setUpdatedAt(LocalDateTime.now());
        return EmergencyContactMapper.toDto(emergencyContactRepo.save(existing));
    }

    @Override
    @Transactional
    public void DeleteEmergencyContact(String contactId) {
        if (!emergencyContactRepo.existsById(contactId)) {
            throw new RecordNotFound("Emergency contact not found with id: " + contactId);
        }
        emergencyContactRepo.deleteById(contactId);
    }

    @Override
    public Page<EmergencyContactResponseDto> getAllContacts(Pageable pageable) {
        return emergencyContactRepo.findAll(pageable).map(EmergencyContactMapper::toDto);
    }

    @Override
    public List<EmergencyContactResponseDto> getAllContactsByUserId(String userId) {
        return emergencyContactRepo.findByUserId(userId).stream()
                .map(EmergencyContactMapper::toDto).toList();
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
}
