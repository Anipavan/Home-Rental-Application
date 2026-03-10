package com.spa.home_rental_application.user_service.user_service.service.impul;

import com.spa.home_rental_application.user_service.user_service.DTO.Request.EmergencyContactRequestDto;
import com.spa.home_rental_application.user_service.user_service.DTO.Response.EmergencyContactResponseDto;
import com.spa.home_rental_application.user_service.user_service.Entities.EmergencyContact;
import com.spa.home_rental_application.user_service.user_service.Entities.User;
import com.spa.home_rental_application.user_service.user_service.mapper.EmergencyContactMapper;
import com.spa.home_rental_application.user_service.user_service.repositry.EmergencyContactRepo;
import com.spa.home_rental_application.user_service.user_service.service.EmergencyContactService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
    public EmergencyContactResponseDto UpdateEmergencyContact(EmergencyContactRequestDto emergencyContactRequestDto, String contactId) {

       EmergencyContact contact=emergencyContactRepo.findById(contactId).get();

       EmergencyContact decriptedrequest=EmergencyContactMapper.toEntity(emergencyContactRequestDto);
       contact.setName(decriptedrequest.getName());
       contact.setPhone(decriptedrequest.getPhone());
       contact.setRelation(decriptedrequest.getRelation());
       contact.setUserId(decriptedrequest.getUserId());
       contact.setUpdatedAt(LocalDateTime.now());
        return EmergencyContactMapper.toDto(emergencyContactRepo.save(contact));
    }

    @Override
    public void DeleteEmergencyContact(String contactId) {
        emergencyContactRepo.deleteById(contactId);
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
