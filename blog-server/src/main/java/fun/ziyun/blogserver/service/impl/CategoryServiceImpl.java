package fun.ziyun.blogserver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import fun.ziyun.blogserver.common.ResultCode;
import fun.ziyun.blogserver.dto.CategoryDTO;
import fun.ziyun.blogserver.entity.Article;
import fun.ziyun.blogserver.entity.ArticleStatus;
import fun.ziyun.blogserver.entity.Category;
import fun.ziyun.blogserver.exception.BusinessException;
import fun.ziyun.blogserver.mapper.ArticleMapper;
import fun.ziyun.blogserver.mapper.CategoryMapper;
import fun.ziyun.blogserver.service.CategoryService;
import fun.ziyun.blogserver.vo.CategoryHotVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 分类服务实现。
 */
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {

    /** 删除分类时的引用检查 */
    private final ArticleMapper articleMapper;

    @Override
    public List<Category> listAll() {
        return this.lambdaQuery()
                .orderByDesc(Category::getCreateTime)
                .list();
    }

    @Override
    public List<CategoryHotVO> listHot(int limit) {
        return baseMapper.selectHotCategories(ArticleStatus.PUBLISHED.getCode(), limit);
    }

    @Override
    public Long create(CategoryDTO dto) {
        checkNameUnique(dto.getName(), null);
        Category category = new Category();
        category.setName(dto.getName());
        category.setDescription(dto.getDescription());
        this.save(category);
        return category.getId();
    }

    @Override
    public void update(Long id, CategoryDTO dto) {
        Category category = this.getById(id);
        if (category == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "分类不存在，id=" + id);
        }
        checkNameUnique(dto.getName(), id);
        category.setName(dto.getName());
        category.setDescription(dto.getDescription());
        category.setUpdateTime(LocalDateTime.now());
        this.updateById(category);
    }

    @Override
    public void delete(Long id) {
        if (this.getById(id) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "分类不存在，id=" + id);
        }
        // 引用检查：分类下还有文章时禁止删除，避免悬空引用
        // （用户必须先迁移或删除该分类下的文章）
        Long count = articleMapper.selectCount(new LambdaQueryWrapper<Article>()
                .eq(Article::getCategoryId, id));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "该分类下还有文章，无法删除");
        }
        this.removeById(id);
    }

    /**
     * 重名校验：先查后插（业务层校验），数据库唯一索引 uk_name 兜底。
     * 两层防线原因：并发下两次查询可能同时通过，唯一索引抛异常兜底；
     * 而日常单用户场景，业务层校验能给出更友好的中文错误消息。
     */
    private void checkNameUnique(String name, Long excludeId) {
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<Category>()
                .eq(Category::getName, name)
                .ne(excludeId != null, Category::getId, excludeId);
        if (this.count(wrapper) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "分类名已存在：" + name);
        }
    }
}
