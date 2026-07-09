package com.lumenami.backend.controller;

import com.lumenami.backend.dto.Result;
import com.lumenami.backend.model.PetMemory;
import com.lumenami.backend.service.MemoryService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/memories")
@RequiredArgsConstructor
public class MemoryController {

    private final MemoryService memoryService;

    /**
     * 获取宠物的所有记忆（用于前端展示）
     */
    @GetMapping("/pet/{petId}")
    public Result<List<PetMemory>> getMemories(
            @RequestHeader("X-User-Id") Integer userId,
            @PathVariable Integer petId) {
        // TODO: 验证用户是否有权限访问该宠物的记忆
        List<PetMemory> memories = memoryService.getMemories(petId);
        return Result.success(memories);
    }

    /**
     * 获取某个key的记忆历史（所有版本）
     */
    @GetMapping("/pet/{petId}/history/{key}")
    public Result<List<PetMemory>> getMemoryHistory(
            @RequestHeader("X-User-Id") Integer userId,
            @PathVariable Integer petId,
            @PathVariable String key) {
        List<PetMemory> history = memoryService.getMemoryHistory(petId, key);
        return Result.success(history);
    }

    /**
     * 删除记忆（手动修正）
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteMemory(
            @RequestHeader("X-User-Id") Integer userId,
            @PathVariable Integer id) {
        memoryService.deleteMemory(id);
        return Result.success(null);
    }

    /**
     * 更新记忆（手动修正）
     */
    @PutMapping("/{id}")
    public Result<Void> updateMemory(
            @RequestHeader("X-User-Id") Integer userId,
            @PathVariable Integer id,
            @RequestBody UpdateMemoryRequest request) {
        memoryService.updateMemory(id, request.getValue(), request.getImportance());
        return Result.success(null);
    }

    /**
     * 手动添加记忆
     */
    @PostMapping
    public Result<Void> addMemory(
            @RequestHeader("X-User-Id") Integer userId,
            @RequestBody AddMemoryRequest request) {
        memoryService.saveMemory(
            request.getPetId(), 
            request.getKey(), 
            request.getValue(), 
            request.getType(), 
            request.getImportance()
        );
        return Result.success(null);
    }

    @Data
    public static class UpdateMemoryRequest {
        private String value;
        private Integer importance;
    }

    @Data
    public static class AddMemoryRequest {
        private Integer petId;
        private String key;
        private String value;
        private String type;
        private Integer importance;
    }
}
