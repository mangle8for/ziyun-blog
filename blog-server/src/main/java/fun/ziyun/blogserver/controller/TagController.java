package fun.ziyun.blogserver.controller;

import fun.ziyun.blogserver.common.Result;
import fun.ziyun.blogserver.dto.TagDTO;
import fun.ziyun.blogserver.entity.Tag;
import fun.ziyun.blogserver.service.TagService;
import fun.ziyun.blogserver.vo.TagHotVO;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 标签接口（与 CategoryController 对称）。
 */
@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
@Validated
public class TagController {

    private final TagService tagService;

    @GetMapping
    public Result<List<Tag>> list() {
        return Result.ok(tagService.listAll());
    }

    /**
     * 热门标签（按已发布文章数倒序，首页筛选区 TopN 展示）。
     * 约定同 CategoryController#hot。
     */
    @GetMapping("/hot")
    public Result<List<TagHotVO>> hot(@RequestParam(defaultValue = "20") @Min(1) int limit) {
        return Result.ok(tagService.listHot(Math.min(limit, 50)));
    }

    @PostMapping
    public Result<Long> create(@RequestBody @Valid TagDTO dto) {
        return Result.ok(tagService.create(dto));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable @Min(1) Long id, @RequestBody @Valid TagDTO dto) {
        tagService.update(id, dto);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Min(1) Long id) {
        tagService.delete(id);
        return Result.ok();
    }
}
