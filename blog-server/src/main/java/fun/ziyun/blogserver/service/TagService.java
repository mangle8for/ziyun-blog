package fun.ziyun.blogserver.service;

import com.baomidou.mybatisplus.extension.service.IService;
import fun.ziyun.blogserver.dto.TagDTO;
import fun.ziyun.blogserver.entity.Tag;

import java.util.List;

/**
 * 标签服务。
 */
public interface TagService extends IService<Tag> {

    /** 全量标签列表（文章编辑器的标签选择器使用） */
    List<Tag> listAll();

    /** 新增标签（校验重名） */
    Long create(TagDTO dto);

    /** 更新标签 */
    void update(Long id, TagDTO dto);

    /** 删除标签（自动清理文章-标签关联，避免悬空关联） */
    void delete(Long id);
}
