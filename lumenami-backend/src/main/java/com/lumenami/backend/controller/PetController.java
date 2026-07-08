package com.lumenami.backend.controller;

import com.lumenami.backend.dto.CreatePetRequest;
import com.lumenami.backend.dto.PetResponse;
import com.lumenami.backend.dto.Result;
import com.lumenami.backend.service.PetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pets")
@RequiredArgsConstructor
public class PetController {

    private final PetService petService;

    @PostMapping
    public Result<PetResponse> createPet(
            @RequestHeader("X-User-Id") Integer userId,
            @RequestBody CreatePetRequest request) {
        PetResponse pet = petService.createPet(userId, request);
        return Result.success(pet);
    }

    @GetMapping
    public Result<List<PetResponse>> listPets(@RequestHeader("X-User-Id") Integer userId) {
        List<PetResponse> pets = petService.getPetsByUserId(userId);
        return Result.success(pets);
    }

    @PatchMapping("/{petId}/activate")
    public Result<PetResponse> activatePet(
            @RequestHeader("X-User-Id") Integer userId,
            @PathVariable Integer petId) {
        PetResponse pet = petService.switchPet(userId, petId);
        return Result.success(pet);
    }

    @DeleteMapping("/{petId}")
    public Result<String> deletePet(
            @RequestHeader("X-User-Id") Integer userId,
            @PathVariable Integer petId) {
        petService.deletePet(userId, petId);
        return Result.success("删除成功");
    }
}