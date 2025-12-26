package com.spa.home_rental_application.property_service.property_service.controller;

import com.spa.home_rental_application.property_service.property_service.Entities.Building;
import com.spa.home_rental_application.property_service.property_service.Entities.Flat;
import com.spa.home_rental_application.property_service.property_service.service.FlatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
    public List<Flat> getAllFlats() {

        return flatService.getAllFlats();
    }

    @GetMapping("/flats/{flatId}")
    public Flat getflatById(@PathVariable String flatId) {

        return flatService.getflatById(flatId);
    }
    @PostMapping(
            value = "/flats/create/flat",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Flat createFlat(@RequestBody Flat flat) {
        log.info("Request recieved for creating flat.{}",flat);
        return flatService.createFlat(flat);
    }

    @DeleteMapping("/flats/{flatId}")
    public ResponseEntity<String> deleteBuilding(@PathVariable String flatId) {
        String message = flatService.deleteFlatById(flatId);
        return ResponseEntity.ok(message);
    }

    @GetMapping("/flats/building/{buildId}")
    public List<Flat> getflatsByBuildingId(@PathVariable String buildId){
       List<Flat> flats= flatService.getflatsByBuildingId(buildId);
       return flats;
    }

    @GetMapping("/flats/vacant")
    public List<Flat> getVacentFlats(){

        return flatService.getAllVacentFlats();
    }
    @PostMapping("/flats/{id}/vacate")
    public ResponseEntity<String> makeFlatVacate(@PathVariable String flatId){
        String message=flatService.makeFlatVacate(flatId);
        return  ResponseEntity.ok(message);
    }

    @PutMapping("/flats/{id}/flat")
    public Flat updateFlat(String flatId,Flat flat){
        return flatService.updateFlat(flatId,flat);
    }
}
