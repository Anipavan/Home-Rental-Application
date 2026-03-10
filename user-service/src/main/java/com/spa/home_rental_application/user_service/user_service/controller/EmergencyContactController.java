package com.spa.home_rental_application.user_service.user_service.controller;

import com.spa.home_rental_application.user_service.user_service.DTO.Request.EmergencyContactRequestDto;
import com.spa.home_rental_application.user_service.user_service.DTO.Response.EmergencyContactResponseDto;
import com.spa.home_rental_application.user_service.user_service.service.EmergencyContactService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users/contacts")
public class EmergencyContactController {
    private final EmergencyContactService emergencyContactService;
    public EmergencyContactController(EmergencyContactService emergencyContactService)
    {

        this.emergencyContactService = emergencyContactService;
    }

    @PostMapping("/{userId}")
    public ResponseEntity<EmergencyContactResponseDto> saveUsersEmergencyContact(@RequestBody @Valid EmergencyContactRequestDto emergencyContactRequestDto)
    {
        return ResponseEntity.ok().body(emergencyContactService.saveUsersEmergencyContact(emergencyContactRequestDto));
    }

    @GetMapping("/getContacts")
    public ResponseEntity<Page<EmergencyContactResponseDto>> getAllContacts(@RequestParam(defaultValue = "0") int pagenum, @RequestParam(defaultValue = "10") int pageSize )
    {
        Pageable pageable= PageRequest.of(pagenum, pageSize);
        return  ResponseEntity.ok().body(emergencyContactService.getAllContacts(pageable));
    }

    @GetMapping("/getContacts/{userId}")
    public ResponseEntity<List<EmergencyContactResponseDto>> getAllContactsByUserId(@PathVariable String userId)
    {
        return  ResponseEntity.ok().body(emergencyContactService.getAllContactsByUserId(userId));
    }



    @PutMapping("/contacts/{contactId}")
    public ResponseEntity<EmergencyContactResponseDto> UpdateEmergencyContact(@RequestBody @Valid EmergencyContactRequestDto emergencyContactRequestDto, @PathVariable("contactId") String contactId)
    {
        return ResponseEntity.ok().body(emergencyContactService.UpdateEmergencyContact(emergencyContactRequestDto,contactId));
    }


    @DeleteMapping("/DelteContact/{contactId}")
    public void DeleteEmergencyContact(@PathVariable String contactId)
    {
        emergencyContactService.DeleteEmergencyContact(contactId);
    }
}
