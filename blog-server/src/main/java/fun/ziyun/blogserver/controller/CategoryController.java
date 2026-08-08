package fun.ziyun.blogserver.controller;

import fun.ziyun.blogserver.common.Result;
import fun.ziyun.blogserver.dto.CategoryDTO;
import fun.ziyun.blogserver.entity.Category;
import fun.ziyun.blogserver.service.CategoryService;
import fun.ziyun.blogserver.vo.CategoryHotVO;
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
 * 分类接口。
 * GET 公开（前台分类导航、文章编辑器下拉），写操作 P2 起由 Security 保护。
 */
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Validated
public class CategoryController {

    private final CategoryService categoryService;

    /** 全量分类列表（数量小不分页；前端导航/下拉直接消费） */
    @GetMapping
    public Result<List<Category>> list() {
        return Result.ok(categoryService.listAll());
    }

    /**
     * 热门分类（按已发布文章数倒序，首页筛选区 TopN 展示）。
     * limit 钳制在 1~50：分类是个人博客的小数据维度，超过 50 无意义。
     */
    @GetMapping("/hot")
    public Result<List<CategoryHotVO>> hot(@RequestParam(defaultValue = "8") @Min(1) int limit) {
        return Result.ok(categoryService.listHot(Math.min(limit, 50)));
    }

    @PostMapping
    public Result<Long> create(@RequestBody @Valid CategoryDTO dto) {
        return Result.ok(categoryService.create(dto));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable @Min(1) Long id, @RequestBody @Valid CategoryDTO dto) {
        categoryService.update(id, dto);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Min(1) Long id) {
        categoryService.delete(id);
        return Result.ok();
    }
}
