package com.atguigu.gmall.pms.service;

import com.atguigu.gmall.pms.entity.ProductCategory;
import com.atguigu.gmall.to.PmsProductCategoryWithChildrenItem;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 产品分类 服务类
 * </p>
 *
 * @author Lfy
 * @since 2019-03-19
 */
public interface ProductCategoryService extends IService<ProductCategory> {

//    查询父id
    Map<String, Object> pageCategory(Long parentId, Integer pageSize, Integer pageNum);

//    查出所以分类及子分类
    List<PmsProductCategoryWithChildrenItem> listWithChildren();
}
