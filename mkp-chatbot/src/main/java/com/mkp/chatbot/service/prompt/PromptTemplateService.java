package com.mkp.chatbot.service.prompt;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mkp.chatbot.mapper.PromptTemplateMapper;
import com.mkp.common.entity.PromptTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Prompt 模板管理服务
 *
 * 职责：
 * 1. 按场景加载 Prompt 模板（带 Redis 缓存）
 * 2. 支持缓存失效，实现"修改即生效"
 * 3. 提供管理后台所需的 CRUD 接口
 * 4. 预留 A/B 测试扩展点
 */
@Slf4j
@Service
public class PromptTemplateService {

    private static final String CACHE_KEY_PREFIX = "prompt:template:";

    @Value("${mkp.chatbot.prompt.cache-ttl-minutes:10}")
    private long cacheTtlMinutes;

    @Autowired
    private PromptTemplateMapper promptTemplateMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 获取指定场景的 Prompt 模板内容
     *
     * 加载优先级：
     * 1. Redis 缓存
     * 2. 数据库默认版本
     * 3. 兜底：返回 null（调用方需处理）
     *
     * @param scene 场景标识，如 "default", "qa", "chat", "recommend"
     * @return 模板内容（纯文本，含占位符）
     */
    public String getTemplateContent(String scene) {
        // 1. 尝试从 Redis 缓存读取
        String cacheKey = CACHE_KEY_PREFIX + scene;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("Prompt 模板命中缓存: scene={}", scene);
            return cached;
        }

        // 2. 从数据库加载默认模板
        PromptTemplate template = promptTemplateMapper.selectDefaultByScene(scene);
        if (template == null) {
            log.warn("未找到场景 {} 的默认 Prompt 模板", scene);
            return null;
        }

        // 3. 写入缓存并返回
        String content = template.getContent();
        stringRedisTemplate.opsForValue().set(cacheKey, content, cacheTtlMinutes, TimeUnit.MINUTES);
        log.info("Prompt 模板加载入库缓存: scene={}, version={}, id={}",
                scene, template.getVersion(), template.getId());
        return content;
    }

    /**
     * 获取指定场景的 Prompt 模板（完整对象）
     */
    public PromptTemplate getTemplate(String scene) {
        return promptTemplateMapper.selectDefaultByScene(scene);
    }

    /**
     * 获取指定场景的指定版本模板
     */
    public PromptTemplate getTemplateByVersion(String scene, Integer version) {
        return promptTemplateMapper.selectBySceneAndVersion(scene, version);
    }

    /**
     * 创建新模板（通常用于新增场景或 A/B 测试变体）
     */
    public PromptTemplate createTemplate(PromptTemplate template) {
        // 新模板默认 version=1
        template.setVersion(1);
        template.setIsDefault(0); // 默认不自动设为默认，需手动切换
        template.setStatus(1);
        promptTemplateMapper.insert(template);
        log.info("创建 Prompt 模板: scene={}, name={}, id={}",
                template.getScene(), template.getName(), template.getId());
        return template;
    }

    /**
     * 编辑模板（创建新版本，旧版本保留）
     *
     * 设计意图：
     * - 每次编辑产生新版本，历史版本不删除
     * - 可选择是否将新版本设为默认
     * - 编辑后自动清除缓存，实现实时生效
     */
    public PromptTemplate updateTemplate(Long id, String newContent, boolean setAsDefault) {
        PromptTemplate old = promptTemplateMapper.selectById(id);
        if (old == null) {
            throw new IllegalArgumentException("模板不存在: id=" + id);
        }

        // 创建新版本
        PromptTemplate newVersion = new PromptTemplate();
        newVersion.setScene(old.getScene());
        newVersion.setName(old.getName());
        newVersion.setContent(newContent);
        newVersion.setVersion(old.getVersion() + 1);
        newVersion.setIsDefault(setAsDefault ? 1 : 0);
        newVersion.setStatus(1);
        promptTemplateMapper.insert(newVersion);

        // 如果设为默认，取消旧版本的默认标记
        if (setAsDefault) {
            cancelOldDefault(old.getScene(), newVersion.getId());
        }

        // 清除缓存，使修改实时生效
        evictCache(old.getScene());

        log.info("Prompt 模板更新: scene={}, 旧版本={}, 新版本={}, id={}",
                old.getScene(), old.getVersion(), newVersion.getVersion(), newVersion.getId());
        return newVersion;
    }

    /**
     * 切换指定场景的默认模板版本
     */
    public void switchDefaultVersion(String scene, Long newDefaultId) {
        cancelOldDefault(scene, newDefaultId);
        evictCache(scene);
        log.info("切换默认 Prompt 模板: scene={}, newDefaultId={}", scene, newDefaultId);
    }

    /**
     * 禁用指定模板（紧急下线场景）
     */
    public void disableTemplate(Long id) {
        PromptTemplate template = promptTemplateMapper.selectById(id);
        if (template == null) return;

        template.setStatus(0);
        promptTemplateMapper.updateById(template);
        evictCache(template.getScene());
        log.warn("禁用 Prompt 模板: id={}, scene={}", id, template.getScene());
    }

    /**
     * 查询某场景下的所有版本（管理后台用）
     */
    public List<PromptTemplate> listVersions(String scene) {
        return promptTemplateMapper.selectList(
                new LambdaQueryWrapper<PromptTemplate>()
                        .eq(PromptTemplate::getScene, scene)
                        .orderByDesc(PromptTemplate::getVersion)
        );
    }

    /**
     * 查询所有场景列表（管理后台用）
     */
    public List<String> listScenes() {
        return promptTemplateMapper.selectList(
                new LambdaQueryWrapper<PromptTemplate>()
                        .select(PromptTemplate::getScene)
                        .groupBy(PromptTemplate::getScene)
        ).stream().map(PromptTemplate::getScene).toList();
    }

    // ==================== 内部方法 ====================

    private void cancelOldDefault(String scene, Long excludeId) {
        List<PromptTemplate> oldDefaults = promptTemplateMapper.selectList(
                new LambdaQueryWrapper<PromptTemplate>()
                        .eq(PromptTemplate::getScene, scene)
                        .eq(PromptTemplate::getIsDefault, 1)
                        .ne(PromptTemplate::getId, excludeId)
        );
        for (PromptTemplate t : oldDefaults) {
            t.setIsDefault(0);
            promptTemplateMapper.updateById(t);
        }
    }

    private void evictCache(String scene) {
        stringRedisTemplate.delete(CACHE_KEY_PREFIX + scene);
        log.info("清除 Prompt 模板缓存: scene={}", scene);
    }
}
