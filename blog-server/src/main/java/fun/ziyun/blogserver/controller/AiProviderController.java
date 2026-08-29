package fun.ziyun.blogserver.controller;

import fun.ziyun.blogserver.common.Result;
import fun.ziyun.blogserver.dto.AiProviderDTO;
import fun.ziyun.blogserver.service.AiProviderService;
import fun.ziyun.blogserver.vo.AiProviderVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 供应商管理接口（仅管理员，见 SecurityConfig 中 /api/v1/ai/** 规则）。
 *
 * <p>安全说明：出参 Key 一律脱敏掩码；更新时 Key 留空 = 保留原值。</p>
 */
@RestController
@RequestMapping("/api/v1/ai/providers")
@RequiredArgsConstructor
@Validated
public class AiProviderController {

    private final AiProviderService aiProviderService;

    /** 全量供应商列表（含启用/默认状态，Key 脱敏） */
    @GetMapping
    public Result<List<AiProviderVO>> list() {
        return Result.ok(aiProviderService.listAll());
    }

    @PostMapping
    public Result<Long> create(@RequestBody @Valid AiProviderDTO dto) {
        return Result.ok(aiProviderService.create(dto));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable @Min(1) Long id, @RequestBody @Valid AiProviderDTO dto) {
        aiProviderService.update(id, dto);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Min(1) Long id) {
        aiProviderService.delete(id);
        return Result.ok();
    }

    /** 设为写作默认模型（全局唯一） */
    @PutMapping("/{id}/default")
    public Result<Void> setDefault(@PathVariable @Min(1) Long id) {
        aiProviderService.setDefault(id);
        return Result.ok();
    }

    /**
     * 连通性测试：用第一个模型真实调用一次对话接口。
     * 成功返回「连接成功 · 耗时 · 模型」；失败由全局异常处理器转错误提示。
     */
    @PostMapping("/{id}/test")
    public Result<String> test(@PathVariable @Min(1) Long id) {
        return Result.ok(aiProviderService.testConnect(id));
    }
}
