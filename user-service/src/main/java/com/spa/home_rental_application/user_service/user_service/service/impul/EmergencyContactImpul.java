package com.spa.home_rental_application.user_service.user_service.service.impul;

import com.spa.home_rental_application.user_service.user_service.DTO.Request.EmergencyContactRequestDto;
import com.spa.home_rental_application.user_service.user_service.DTO.Response.EmergencyContactResponseDto;
import com.spa.home_rental_application.user_service.user_service.Entities.EmergencyContact;
import com.spa.home_rental_application.user_service.user_service.mapper.EmergencyContactMapper;
import com.spa.home_rental_application.user_service.user_service.repositry.EmergencyContactRepo;
import com.spa.home_rental_application.user_service.user_service.service.EmergencyContactService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmergencyContactImpul implements EmergencyContactService {
    private final EmergencyContactRepo emergencyContactRepo;
    public EmergencyContactImpul(EmergencyContactRepo emergencyContactRepo)
    {

        this.emergencyContactRepo = emergencyContactRepo;
    }
    @Override
    public EmergencyContactResponseDto saveUsersEmergencyContact(EmergencyContactRequestDto emergencyContactRequestDto) {

        EmergencyContact emergencyContact= EmergencyContactMapper.toEntity(emergencyContactRequestDto);
        return EmergencyContactMapper.toDto(emergencyContactRepo.save(emergencyContact));
    }

    @Override
    public EmergencyContactResponseDto UpdateEmergencyContact(EmergencyContactRequestDto emergencyContactRequestDto) {
        EmergencyContact contact=EmergencyContactMapper.toEntity(emergencyContactRequestDto);

        return EmergencyContactMapper.toDto(emergencyContactRepo.save(contact));
    }

    @Override
    public void DeleteEmergencyContact(String userId) {
        List<EmergencyContact> emergencyContact=emergencyContactRepo.findByUserId(userId);
        String id=emergencyContact.getFirst().getId();
        emergencyContactRepo.deleteById(id);
    }

    @Override
    public Page<EmergencyContactResponseDto> getAllContacts(Pageable pageable) {

        Page<EmergencyContact> emergencyContacts=emergencyContactRepo.findAll(pageable);
        return emergencyContacts.map(EmergencyContactMapper::toDto) ;
    }

    @Override
    public List<EmergencyContactResponseDto> getAllContactsByUserId(String userId) {

        List<EmergencyContact> emergencyContacts=emergencyContactRepo.findByUserId(userId);

        return emergencyContacts.stream().map(contact-> EmergencyContactMapper.toDto(contact)).toList();
    }
}
