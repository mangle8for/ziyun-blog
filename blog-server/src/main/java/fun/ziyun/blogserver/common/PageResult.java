package fun.ziyun.blogserver.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页查询结果包装。
 *
 * <p>设计说明（为什么单独包装分页而非塞进 Result.data）：</p>
 * <pre>
 * 分页响应和普通响应的结构不同：除了数据列表，还要带 total/page/size 三个
 * 元信息供前端渲染分页器。若不包装，前端每次都要自己拼一个 {list, total}
 * 的匿名对象，类型和安全都没保障。PageResult 固定结构后：
 *   - 前端类型定义与后端一一对应（TS 侧照抄）；
 *   - 提供 from(IPage) 静态方法，直接消费 MyBatis-Plus 的 Page 对象，
 *     Controller 里只需一行转换。
 * </pre>
 *
 * @param <T> 列表中元素的类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    /** 当前页数据列表 */
    private List<T> records;

    /** 总记录数（MySQL 端 COUNT 查询得到） */
    private Long total;

    /** 当前页码（从 1 开始） */
    private Long page;

    /** 每页大小 */
    private Long size;

    /** 是否还有下一页（前端"加载更多"场景用，也可省略由前端按 total 自算） */
    private Boolean hasNext;

    /**
     * 由 MyBatis-Plus 的 IPage 转换而来。
     *
     * <p>设计说明：IPage 是 MP 分页插件执行完 SQL 后封装的结果集，
     * 内部字段（current/size/pages 等）命名偏框架化，直接暴露给前端
     * 会把框架细节泄漏到 API 层；这里只挑前端需要的字段转出来。</p>
     */
    public static <T> PageResult<T> from(IPage<T> page) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(page.getRecords());
        result.setTotal(page.getTotal());
        result.setPage(page.getCurrent());
        result.setSize(page.getSize());
        result.setHasNext(page.getCurrent() < page.getPages());
        return result;
    }
}
