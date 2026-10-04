package com.mkp.chatbot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkp.common.entity.PromptTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PromptTemplateMapper extends BaseMapper<PromptTemplate> {

    /**
     * 获取指定场景下默认启用的模板
     */
    @Select("SELECT * FROM mkp_prompt_template " +
            "WHERE scene = #{scene} AND is_default = 1 AND status = 1 " +
            "ORDER BY version DESC LIMIT 1")
    PromptTemplate selectDefaultByScene(@Param("scene") String scene);

    /**
     * 获取指定场景下指定版本的模板
     */
    @Select("SELECT * FROM mkp_prompt_template " +
            "WHERE scene = #{scene} AND version = #{version} AND status = 1")
    PromptTemplate selectBySceneAndVersion(@Param("scene") String scene,
                                            @Param("version") Integer version);
}
