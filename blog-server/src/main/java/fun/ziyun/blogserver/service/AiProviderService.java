package fun.ziyun.blogserver.service;

import com.baomidou.mybatisplus.extension.service.IService;
import fun.ziyun.blogserver.dto.AiProviderDTO;
import fun.ziyun.blogserver.entity.AiProvider;
import fun.ziyun.blogserver.vo.AiProviderVO;

import java.util.List;

/**
 * AI 供应商服务：管理端 CRUD + 写作默认模型 + 连通性测试。
 */
public interface AiProviderService extends IService<AiProvider> {

    /** 全量供应商列表（Key 脱敏），按「默认优先 + 创建时间」排序 */
    List<AiProviderVO> listAll();

    /** 新增供应商，返回 ID（首个供应商自动设为写作默认） */
    Long create(AiProviderDTO dto);

    /** 更新供应商：apiKey 留空 = 保留原 Key */
    void update(Long id, AiProviderDTO dto);

    /** 删除供应商（逻辑删除）；删除的是默认供应商时无需补位（下一次设置默认即可） */
    void delete(Long id);

    /** 设为写作默认（全局唯一，其余自动取消） */
    void setDefault(Long id);

    /**
     * 连通性测试：用该供应商的第一个模型真实调用一次对话接口。
     *
     * @return 人类可读的测试结果（成功含耗时，失败含原因）
     */
    String testConnect(Long id);

    /**
     * 取「写作默认」且启用的供应商（解密后的明文 Key 仅供 AI 调用链使用）。
     *
     * @throws fun.ziyun.blogserver.exception.BusinessException 未配置默认供应商或已停用时
     */
    AiProvider getDefaultEnabled();
}
