package com.qpet.backend.controller;

import com.qpet.backend.dto.CreatePetRequest;
import com.qpet.backend.dto.PetResponse;
import com.qpet.backend.dto.Result;
import com.qpet.backend.service.PetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pets")
@RequiredArgsConstructor
public class PetController {

    private final PetService petService;

    private Integer getCurrentUserId() {
        return 1;
    }

    @PostMapping
    public Result<PetResponse> createPet(@RequestBody CreatePetRequest request) {
        PetResponse pet = petService.createPet(getCurrentUserId(), request);
        return Result.success(pet);
    }

    @GetMapping
    public Result<List<PetResponse>> listPets() {
        List<PetResponse> pets = petService.getPetsByUserId(getCurrentUserId());
        return Result.success(pets);
    }

    @PatchMapping("/{petId}/activate")
    public Result<PetResponse> activatePet(@PathVariable Integer petId) {
        PetResponse pet = petService.switchPet(getCurrentUserId(), petId);
        return Result.success(pet);
    }

    @DeleteMapping("/{petId}")
    public Result<String> deletePet(@PathVariable Integer petId) {
        petService.deletePet(getCurrentUserId(), petId);
        return Result.success("删除成功");
    }
}