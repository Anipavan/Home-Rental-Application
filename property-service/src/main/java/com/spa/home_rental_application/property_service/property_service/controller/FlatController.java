package com.spa.home_rental_application.property_service.property_service.controller;

import com.spa.home_rental_application.property_service.property_service.DTO.Request.FlatRequestDTO;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.FlatResponseDTO;
import com.spa.home_rental_application.property_service.property_service.Entities.Building;
import com.spa.home_rental_application.property_service.property_service.Entities.Flat;
import com.spa.home_rental_application.property_service.property_service.service.FlatService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/properties", produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class FlatController {
    @Autowired
    FlatService flatService;
    @GetMapping("/flats")
    public ResponseEntity<Page<FlatResponseDTO>> getAllFlats(@RequestParam(defaultValue = "0") int pagenum,@RequestParam(defaultValue = "10") int pagesize) {

       Pageable pageable= PageRequest.of(pagenum,pagesize);

        return ResponseEntity.ok().body(flatService.getAllFlats(pageable));
    }

    @GetMapping("/flats/{flatId}")
    public ResponseEntity<FlatResponseDTO> getflatById(@PathVariable String flatId) {

        return ResponseEntity.ok().body(flatService.getflatById(flatId));
    }
    @PostMapping(
            value = "/flats/create/flat",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public  ResponseEntity<FlatResponseDTO> createFlat(@RequestBody @Valid FlatRequestDTO flatRequestDTO) {

        log.info("Request recieved for creating flat.{}",flatRequestDTO);
        return ResponseEntity.ok().body(flatService.createFlat(flatRequestDTO));
    }

    @DeleteMapping("/flats/{flatId}")
    public ResponseEntity<Flat> deleteBuilding(@PathVariable String flatId) {
        Flat flat = flatService.deleteFlatById(flatId);
        return ResponseEntity.ok(flat);
    }

    @GetMapping("/flats/building/{buildId}")
    public ResponseEntity<List<FlatResponseDTO>> getflatsByBuildingId(@PathVariable String buildId){
       return ResponseEntity.ok().body( flatService.getflatsByBuildingId(buildId));

    }

    @GetMapping("/flats/vacant")
    public ResponseEntity<List<FlatResponseDTO> >getVacentFlats(){

        return ResponseEntity.ok().body(flatService.getAllVacentFlats());
    }
    @PostMapping("/flats/{id}/vacate")
    public ResponseEntity<FlatResponseDTO> makeFlatVacate(@PathVariable String flatId){

        return  ResponseEntity.ok().body(flatService.makeFlatVacate(flatId));
    }

    @PutMapping("/flats/{id}/flat")
    public ResponseEntity<FlatResponseDTO> updateFlat(String flatId,@RequestBody @Valid FlatRequestDTO flatRequestDTO){
        return ResponseEntity.ok().body(flatService.updateFlat(flatId,flatRequestDTO));
    }

    @PostMapping("/flats/{id}/assign")
    public ResponseEntity<Flat> assignFlat(@PathVariable("id") String userId){
        //Flat flatDetails=flatService.assignFlat(userId);
       // return  ResponseEntity.ok().body(flatDetails);
        return null;
    }
}
