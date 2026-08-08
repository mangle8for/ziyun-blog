package fun.ziyun.blogserver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import fun.ziyun.blogserver.common.ResultCode;
import fun.ziyun.blogserver.dto.TagDTO;
import fun.ziyun.blogserver.entity.ArticleStatus;
import fun.ziyun.blogserver.entity.Tag;
import fun.ziyun.blogserver.exception.BusinessException;
import fun.ziyun.blogserver.mapper.ArticleTagMapper;
import fun.ziyun.blogserver.mapper.TagMapper;
import fun.ziyun.blogserver.service.TagService;
import fun.ziyun.blogserver.vo.TagHotVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 标签服务实现。
 * 与 CategoryServiceImpl 结构对称，可对照学习（差异在删除策略：
 * 标签是弱关联，删除时清关联即可；分类是强引用，删除需拒绝）。
 */
@Service
@RequiredArgsConstructor
public class TagServiceImpl extends ServiceImpl<TagMapper, Tag> implements TagService {

    private final ArticleTagMapper articleTagMapper;

    @Override
    public List<Tag> listAll() {
        return this.lambdaQuery()
                .orderByDesc(Tag::getCreateTime)
                .list();
    }

    @Override
    public List<TagHotVO> listHot(int limit) {
        return baseMapper.selectHotTags(ArticleStatus.PUBLISHED.getCode(), limit);
    }

    @Override
    public Long create(TagDTO dto) {
        checkNameUnique(dto.getName(), null);
        Tag tag = new Tag();
        tag.setName(dto.getName());
        this.save(tag);
        return tag.getId();
    }

    @Override
    public void update(Long id, TagDTO dto) {
        Tag tag = this.getById(id);
        if (tag == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "标签不存在，id=" + id);
        }
        checkNameUnique(dto.getName(), id);
        tag.setName(dto.getName());
        tag.setUpdateTime(LocalDateTime.now());
        this.updateById(tag);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (this.getById(id) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "标签不存在，id=" + id);
        }
        // 先清关联再删标签：article_tag 中间表记录随之物理删除，
        // 不会出现「文章详情里挂着已删除标签」的悬空引用
        articleTagMapper.deleteByTagId(id);
        this.removeById(id);
    }

    /** 重名校验（与 CategoryServiceImpl 同思路） */
    private void checkNameUnique(String name, Long excludeId) {
        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<Tag>()
                .eq(Tag::getName, name)
                .ne(excludeId != null, Tag::getId, excludeId);
        if (this.count(wrapper) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "标签名已存在：" + name);
        }
    }
}
