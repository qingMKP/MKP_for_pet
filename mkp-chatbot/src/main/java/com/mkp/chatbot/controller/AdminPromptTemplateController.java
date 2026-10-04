package com.mkp.chatbot.controller;

import com.mkp.chatbot.service.prompt.PromptTemplateService;
import com.mkp.common.entity.PromptTemplate;
import com.mkp.common.result.R;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Prompt 模板管理后台接口
 *
 * 权限：建议仅 ADMIN 角色可访问（通过 Gateway 路由或方法级注解控制）
 */
@RestController
@RequestMapping("/api/admin/chatbot/prompt")
public class AdminPromptTemplateController {

    private final PromptTemplateService promptTemplateService;

    public AdminPromptTemplateController(PromptTemplateService promptTemplateService) {
        this.promptTemplateService = promptTemplateService;
    }

    /**
     * 获取指定场景的当前默认模板
     */
    @GetMapping("/{scene}")
    public R<PromptTemplate> getTemplate(@PathVariable String scene) {
        PromptTemplate template = promptTemplateService.getTemplate(scene);
        return template != null ? R.ok(template) : R.fail("未找到该场景的模板");
    }

    /**
     * 获取指定场景的所有版本（历史版本列表）
     */
    @GetMapping("/{scene}/versions")
    public R<List<PromptTemplate>> listVersions(@PathVariable String scene) {
        return R.ok(promptTemplateService.listVersions(scene));
    }

    /**
     * 创建新模板（新场景或 A/B 变体）
     */
    @PostMapping
    public R<PromptTemplate> createTemplate(@RequestBody PromptTemplate template) {
        return R.ok(promptTemplateService.createTemplate(template));
    }

    /**
     * 编辑模板（创建新版本）
     *
     * @param setAsDefault 是否将新版本设为默认
     */
    @PostMapping("/{id}/update")
    public R<PromptTemplate> updateTemplate(
            @PathVariable Long id,
            @RequestParam String newContent,
            @RequestParam(defaultValue = "true") boolean setAsDefault) {
        return R.ok(promptTemplateService.updateTemplate(id, newContent, setAsDefault));
    }

    /**
     * 切换默认版本
     */
    @PostMapping("/switch-default")
    public R<Void> switchDefault(@RequestParam String scene, @RequestParam Long templateId) {
        promptTemplateService.switchDefaultVersion(scene, templateId);
        return R.ok();
    }

    /**
     * 禁用模板（紧急下线）
     */
    @PostMapping("/{id}/disable")
    public R<Void> disableTemplate(@PathVariable Long id) {
        promptTemplateService.disableTemplate(id);
        return R.ok();
    }

    /**
     * 获取所有场景列表
     */
    @GetMapping("/scenes")
    public R<List<String>> listScenes() {
        return R.ok(promptTemplateService.listScenes());
    }
}
