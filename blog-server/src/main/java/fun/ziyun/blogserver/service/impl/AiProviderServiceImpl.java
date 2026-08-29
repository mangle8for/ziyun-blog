package fun.ziyun.blogserver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fun.ziyun.blogserver.config.AiProperties;
import fun.ziyun.blogserver.dto.AiProviderDTO;
import fun.ziyun.blogserver.entity.AiProvider;
import fun.ziyun.blogserver.exception.BusinessException;
import fun.ziyun.blogserver.common.ResultCode;
import fun.ziyun.blogserver.mapper.AiProviderMapper;
import fun.ziyun.blogserver.service.AiProviderService;
import fun.ziyun.blogserver.util.AiCryptoUtil;
import fun.ziyun.blogserver.vo.AiProviderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * AI 供应商服务实现：CRUD、默认模型互斥、连通性测试。
 *
 * <p>安全设计：明文 Key 只在两个瞬间存在 —— 保存前加密、调用前解密；
 * 接口出参一律脱敏掩码，库中只存密文。</p>
 */
@Service
@RequiredArgsConstructor
public class AiProviderServiceImpl extends ServiceImpl<AiProviderMapper, AiProvider>
        implements AiProviderService {

    private final AiCryptoUtil aiCryptoUtil;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    @Override
    public List<AiProviderVO> listAll() {
        // 默认的排最前，其余按创建先后，保证前端列表顺序稳定
        LambdaQueryWrapper<AiProvider> wrapper = new LambdaQueryWrapper<AiProvider>()
                .orderByDesc(AiProvider::getIsDefault)
                .orderByAsc(AiProvider::getCreateTime);
        return list(wrapper).stream().map(this::toVo).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(AiProviderDTO dto) {
        if (dto.getApiKey() == null || dto.getApiKey().isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "API Key 不能为空");
        }
        AiProvider provider = new AiProvider();
        applyDto(provider, dto);
        provider.setApiKey(aiCryptoUtil.encrypt(dto.getApiKey().trim()));
        // 首个供应商自动成为写作默认，省去一次额外设置
        provider.setIsDefault(count() == 0 ? 1 : 0);
        provider.setEnabled(dto.getEnabled() == null ? 1 : dto.getEnabled());
        save(provider);
        if (Integer.valueOf(1).equals(provider.getIsDefault())) {
            clearOtherDefaults(provider.getId());
        }
        return provider.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, AiProviderDTO dto) {
        AiProvider provider = getEntityById(id);
        applyDto(provider, dto);
        // Key 留空 = 保留原值（前端拿到的是掩码，无法也不应回传明文）
        if (dto.getApiKey() != null && !dto.getApiKey().isBlank()) {
            provider.setApiKey(aiCryptoUtil.encrypt(dto.getApiKey().trim()));
        }
        provider.setUpdateTime(null);
        updateById(provider);
    }

    @Override
    public void delete(Long id) {
        getEntityById(id);
        removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long id) {
        AiProvider provider = getEntityById(id);
        clearOtherDefaults(id);
        provider.setIsDefault(1);
        provider.setUpdateTime(null);
        updateById(provider);
    }

    @Override
    public String testConnect(Long id) {
        AiProvider provider = getEntityById(id);
        String model = firstModel(provider);
        String plainKey = aiCryptoUtil.decrypt(provider.getApiKey());

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("stream", false);
        body.put("max_tokens", 8);
        ArrayNode messages = body.putArray("messages");
        ObjectNode message = messages.addObject();
        message.put("role", "user");
        message.put("content", "ping");

        long start = System.currentTimeMillis();
        try {
            HttpRequest request = buildRequest(provider.getBaseUrl(), plainKey, body);
            HttpResponse<String> response = createClient(Duration.ofSeconds(15))
                    .send(request, HttpResponse.BodyHandlers.ofString());
            long cost = System.currentTimeMillis() - start;
            if (response.statusCode() != 200) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                        "上游返回 " + response.statusCode() + "：" + abbreviate(response.body()));
            }
            return "连接成功 · " + cost + "ms · 模型 " + model;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "连接失败：" + rootMessage(e));
        }
    }

    @Override
    public AiProvider getDefaultEnabled() {
        AiProvider provider = getOne(new LambdaQueryWrapper<AiProvider>()
                .eq(AiProvider::getIsDefault, 1)
                .eq(AiProvider::getEnabled, 1)
                .last("LIMIT 1"));
        if (provider == null) {
            throw new BusinessException(ResultCode.NOT_FOUND,
                    "尚未配置可用的写作模型，请先到「AI 设置」添加供应商");
        }
        return provider;
    }

    // ---------- 内部工具 ----------

    /** DTO -> 实体（不含 apiKey / isDefault / 主键，由调用方按语义单独处理） */
    private void applyDto(AiProvider provider, AiProviderDTO dto) {
        provider.setName(dto.getName().trim());
        provider.setBaseUrl(normalizeBaseUrl(dto.getBaseUrl()));
        provider.setModels(writeModels(dto.getModels()));
        provider.setEnabled(dto.getEnabled() == null ? 1 : dto.getEnabled());
        provider.setRemark(dto.getRemark());
    }

    /** 统一 Base URL：去尾斜杠；用户粘贴了完整对话端点时剥掉，保存统一前缀形态 */
    private String normalizeBaseUrl(String url) {
        String trimmed = url.trim();
        if (trimmed.endsWith("/chat/completions")) {
            trimmed = trimmed.substring(0, trimmed.length() - "/chat/completions".length());
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Base URL 必须以 http(s):// 开头");
        }
        return trimmed;
    }

    private void clearOtherDefaults(Long excludeId) {
        lambdaUpdate()
                .eq(AiProvider::getIsDefault, 1)
                .ne(AiProvider::getId, excludeId)
                .set(AiProvider::getIsDefault, 0)
                .update();
    }

    private AiProviderVO toVo(AiProvider provider) {
        AiProviderVO vo = new AiProviderVO();
        vo.setId(provider.getId());
        vo.setName(provider.getName());
        vo.setBaseUrl(provider.getBaseUrl());
        vo.setApiKeyMasked(maskKey(provider.getApiKey()));
        vo.setModels(readModels(provider));
        vo.setEnabled(provider.getEnabled());
        vo.setIsDefault(provider.getIsDefault());
        vo.setRemark(provider.getRemark());
        vo.setUpdateTime(provider.getUpdateTime());
        return vo;
    }

    /** 掩码：保留前 3 后 4（如 sk-****abcd），过短全掩码 */
    private String maskKey(String encryptedKey) {
        try {
            String plain = aiCryptoUtil.decrypt(encryptedKey);
            if (plain.length() <= 8) {
                return "****";
            }
            return plain.substring(0, 3) + "****" + plain.substring(plain.length() - 4);
        } catch (Exception e) {
            // 解密失败（如换过加密密钥）不阻塞列表展示
            return "****";
        }
    }

    private List<String> readModels(AiProvider provider) {
        try {
            return objectMapper.readValue(provider.getModels(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    private String writeModels(List<String> models) {
        try {
            List<String> distinct = models.stream().map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
            if (distinct.isEmpty()) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "至少添加一个模型");
            }
            return objectMapper.writeValueAsString(distinct);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("模型列表序列化失败", e);
        }
    }

    private String firstModel(AiProvider provider) {
        List<String> models = readModels(provider);
        if (models.isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "该供应商未配置模型");
        }
        return models.get(0);
    }

    private AiProvider getEntityById(Long id) {
        AiProvider provider = getById(id);
        if (provider == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "供应商不存在，id=" + id);
        }
        return provider;
    }

    /** 供应商 CRUD 与连通性测试共用的请求构造（对话流式接口在 AiChatServiceImpl 另建） */
    private HttpRequest buildRequest(String baseUrl, String plainKey, ObjectNode body) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + plainKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
    }

    private HttpClient createClient(Duration connectTimeout) {
        return HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        String compact = text.replaceAll("\\s+", " ").trim();
        return compact.length() > 160 ? compact.substring(0, 160) + "…" : compact;
    }

    private String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage() == null ? cur.getClass().getSimpleName() : cur.getMessage();
    }
}
