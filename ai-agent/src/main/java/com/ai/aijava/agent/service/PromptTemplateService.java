package com.ai.aijava.agent.service;

import com.ai.aijava.agent.dto.request.PromptTemplateCreateRequest;
import com.ai.aijava.agent.dto.request.PromptTemplateUpdateRequest;
import com.ai.aijava.agent.dto.vo.PromptTemplateVO;
import com.ai.aijava.agent.entity.KnowledgeBase;
import com.ai.aijava.agent.entity.PromptTemplate;
import com.ai.aijava.agent.mapper.KnowledgeBaseMapper;
import com.ai.aijava.agent.mapper.PromptTemplateMapper;
import com.ai.aijava.context.UserContext;
import com.ai.aijava.exception.BusinessException;
import com.ai.aijava.exception.ErrorCode;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.util.UpdateEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 提示词模板服务：CRUD + KB 绑定渲染 + 变量白名单校验
 *
 * 渲染引擎为 String.replace 字面替换（设计 2.2）：变量是封闭白名单，
 * 模板正文字面 { }（如 JSON 示例）安全，渲染永不抛异常。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptTemplateService {

    /** system_template 可用变量 */
    public static final Set<String> SYSTEM_VARS = Set.of("kbName", "kbDescription");

    /** user_template 可用变量 */
    public static final Set<String> USER_VARS = Set.of("question", "references", "referencesBlock");

    /** 默认系统模板（原 AgentProperties.DEFAULT_SYSTEM_PROMPT 原文迁移，保证行为等价） */
    public static final String DEFAULT_SYSTEM_TEMPLATE =
            "你是知识库问答助手，仅依据参考资料回答问题；"
                    + "回答末尾不需要提及参考资料本身；"
                    + "当参考资料未覆盖提问内容时，明确说明未在知识库中找到直接依据。";

    /** 默认用户模板（渲染结果与改造前硬编码拼接逐字节等价） */
    public static final String DEFAULT_USER_TEMPLATE = "{referencesBlock}问题：{question}";

    /** 提取 {var} 占位符 */
    private static final Pattern VAR_PATTERN = Pattern.compile("\\{([a-zA-Z]+)}");

    private final PromptTemplateMapper promptTemplateMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;

    /**
     * 创建模板（变量白名单校验）
     */
    public PromptTemplateVO create(PromptTemplateCreateRequest request) {
        validateVars(request.getSystemTemplate(), SYSTEM_VARS, "系统提示词模板");
        validateVars(request.getUserTemplate(), USER_VARS, "用户消息模板");
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "未登录");
        }
        LocalDateTime now = LocalDateTime.now();
        PromptTemplate tpl = PromptTemplate.builder()
                .userId(userId)
                .name(request.getName())
                .description(request.getDescription())
                .systemTemplate(request.getSystemTemplate())
                .userTemplate(request.getUserTemplate())
                .createTime(now)
                .updateTime(now)
                .build();
        promptTemplateMapper.insert(tpl);
        return toVO(tpl);
    }

    /**
     * 我的模板列表
     */
    public List<PromptTemplateVO> list() {
        return promptTemplateMapper.selectListByQuery(
                        QueryWrapper.create()
                                .where(PromptTemplate::getUserId).eq(UserContext.getUserId())
                                .orderBy(PromptTemplate::getUpdateTime, false))
                .stream().map(this::toVO).toList();
    }

    /**
     * 修改模板
     */
    public void update(PromptTemplateUpdateRequest request) {
        PromptTemplate tpl = getOwnedTemplate(request.getId());
        validateVars(request.getSystemTemplate(), SYSTEM_VARS, "系统提示词模板");
        validateVars(request.getUserTemplate(), USER_VARS, "用户消息模板");
        PromptTemplate update = new PromptTemplate();
        update.setId(tpl.getId());
        update.setName(request.getName());
        update.setDescription(request.getDescription());
        update.setSystemTemplate(request.getSystemTemplate());
        update.setUserTemplate(request.getUserTemplate());
        promptTemplateMapper.update(update);
    }

    /**
     * 删除模板：先解绑引用它的 KB（prompt_template_id 置空），再删记录。
     * 解绑 + 删除放同一事务；置空必须走 UpdateEntity（常规 update 忽略 null 字段）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PromptTemplate tpl = getOwnedTemplate(id);
        KnowledgeBase unbind = UpdateEntity.of(KnowledgeBase.class);
        unbind.setPromptTemplateId(null);
        knowledgeBaseMapper.updateByQuery(unbind, QueryWrapper.create()
                .where(KnowledgeBase::getPromptTemplateId).eq(id)
                .and(KnowledgeBase::getUserId).eq(UserContext.getUserId()));
        promptTemplateMapper.deleteById(id);
        log.info("提示词模板已删除并解绑知识库 templateId={}", id);
    }

    /**
     * 渲染 SystemMessage 文本（未绑定/模板已删/归属不符 → 默认模板）
     */
    public String renderSystem(KnowledgeBase kb) {
        PromptTemplate tpl = loadBoundTemplate(kb);
        String template = tpl != null ? tpl.getSystemTemplate() : DEFAULT_SYSTEM_TEMPLATE;
        return render(template, Map.of(
                "kbName", nullToEmpty(kb.getName()),
                "kbDescription", nullToEmpty(kb.getDescription())));
    }

    /**
     * 渲染 UserMessage 文本
     */
    public String renderUser(KnowledgeBase kb, String references, String question) {
        PromptTemplate tpl = loadBoundTemplate(kb);
        String template = tpl != null ? tpl.getUserTemplate() : DEFAULT_USER_TEMPLATE;
        String refs = references == null ? "" : references;
        String refsBlock = refs.isBlank() ? "" : "参考资料：\n" + refs + "\n\n";
        return render(template, Map.of(
                "question", nullToEmpty(question),
                "references", refs,
                "referencesBlock", refsBlock));
    }

    /**
     * 校验模板归属当前用户
     */
    public PromptTemplate getOwnedTemplate(Long id) {
        PromptTemplate tpl = promptTemplateMapper.selectOneById(id);
        if (tpl == null || !tpl.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "模板不存在");
        }
        return tpl;
    }

    /**
     * 加载 KB 绑定的模板：NULL / 已删 / 归属不符均回退 null（默认模板）
     */
    private PromptTemplate loadBoundTemplate(KnowledgeBase kb) {
        if (kb.getPromptTemplateId() == null) {
            return null;
        }
        PromptTemplate tpl = promptTemplateMapper.selectOneById(kb.getPromptTemplateId());
        // 归属不符属异常数据（绑定时有校验 + 删除时解绑），防御性回退默认
        if (tpl == null || !tpl.getUserId().equals(kb.getUserId())) {
            log.warn("知识库绑定模板异常，回退默认模板 kbId={} templateId={}",
                    kb.getId(), kb.getPromptTemplateId());
            return null;
        }
        return tpl;
    }

    /**
     * 字面替换渲染（String.replace，变量为封闭白名单，永不抛异常）
     */
    private String render(String template, Map<String, String> vars) {
        String result = template;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    /**
     * 变量白名单校验：未知 {xxx} 直接拦截，防拼错占位符静默渲染出字面量
     */
    private void validateVars(String template, Set<String> allowed, String field) {
        Matcher matcher = VAR_PATTERN.matcher(template);
        while (matcher.find()) {
            String var = matcher.group(1);
            if (!allowed.contains(var)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR,
                        field + " 含未知变量 {" + var + "}，可用变量：" + allowed);
            }
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private PromptTemplateVO toVO(PromptTemplate tpl) {
        return PromptTemplateVO.builder()
                .id(tpl.getId())
                .name(tpl.getName())
                .description(tpl.getDescription())
                .systemTemplate(tpl.getSystemTemplate())
                .userTemplate(tpl.getUserTemplate())
                .createTime(tpl.getCreateTime())
                .updateTime(tpl.getUpdateTime())
                .build();
    }
}
