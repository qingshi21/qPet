package com.lumenami.backend.service;

import com.lumenami.backend.dto.CreatePetRequest;
import com.lumenami.backend.dto.PetResponse;
import com.lumenami.backend.exception.BusinessException;
import com.lumenami.backend.mapper.PetMapper;
import com.lumenami.backend.model.Pet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PetService {

    private final PetMapper petMapper;

    @Transactional
    public PetResponse createPet(Integer userId, CreatePetRequest request) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new BusinessException(400, "宠物名称不能为空");
        }
        if (request.getSystemPrompt() == null || request.getSystemPrompt().trim().isEmpty()) {
            throw new BusinessException(400, "System Prompt 不能为空");
        }

        int count = petMapper.countByNameAndUserId(userId, request.getName());
        if (count > 0) {
            throw new BusinessException(400, "已存在同名宠物");
        }

        Pet pet = new Pet();
        pet.setUserId(userId);
        pet.setName(request.getName());
        pet.setRoleName(request.getRoleName());
        pet.setSystemPrompt(request.getSystemPrompt());
        pet.setIsActive(0);
        petMapper.insert(pet);

        return toResponse(pet);
    }

    public List<PetResponse> getPetsByUserId(Integer userId) {
        return petMapper.findByUserId(userId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public PetResponse switchPet(Integer userId, Integer petId) {
        Pet pet = petMapper.findById(petId);
        if (pet == null || !pet.getUserId().equals(userId)) {
            throw new BusinessException(404, "宠物不存在");
        }

        petMapper.deactivateAllByUserId(userId);
        petMapper.activate(petId);

        Pet updated = petMapper.findById(petId);
        return toResponse(updated);
    }

    @Transactional
    public void deletePet(Integer userId, Integer petId) {
        Pet pet = petMapper.findById(petId);
        if (pet == null || !pet.getUserId().equals(userId)) {
            throw new BusinessException(404, "宠物不存在");
        }
        petMapper.deleteById(petId);
    }

    private PetResponse toResponse(Pet pet) {
        PetResponse resp = new PetResponse();
        resp.setPetId(pet.getId());
        resp.setName(pet.getName());
        resp.setRoleName(pet.getRoleName());
        resp.setIsActive(pet.getIsActive() == 1);
        return resp;
    }
}