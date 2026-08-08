package fun.ziyun.blogserver.service;

import com.baomidou.mybatisplus.extension.service.IService;
import fun.ziyun.blogserver.dto.CategoryDTO;
import fun.ziyun.blogserver.entity.Category;
import fun.ziyun.blogserver.vo.CategoryHotVO;

import java.util.List;

/**
 * 分类服务：BaseMapper 内置 CRUD 之外仅需少量业务校验，故接口极薄。
 */
public interface CategoryService extends IService<Category> {

    /** 全量分类列表（按创建时间倒序；个人博客分类数小，不做分页） */
    List<Category> listAll();

    /** 热门分类（按已发布文章数倒序取 TopN，首页筛选区展示用） */
    List<CategoryHotVO> listHot(int limit);

    /** 新增分类（校验重名） */
    Long create(CategoryDTO dto);

    /** 更新分类（校验存在与重名） */
    void update(Long id, CategoryDTO dto);

    /**
     * 删除分类：被文章引用时拒绝删除（逻辑外键的完整性约束，
     * 防止删了分类导致文章列表出现「分类为空」的悬空引用）。
     */
    void delete(Long id);
}
