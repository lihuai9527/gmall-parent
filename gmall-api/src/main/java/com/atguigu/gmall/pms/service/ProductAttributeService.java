package com.atguigu.gmall.pms.service;

import com.atguigu.gmall.pms.entity.ProductAttribute;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Map;

/**
 * <p>
 * 商品属性参数表 服务类
 * </p>
 *
 * @author Lfy
 * @since 2019-03-19
 */
public interface ProductAttributeService extends IService<ProductAttribute> {
//分页查询销售属性或基本属性
//    type : 0表示销售属性，1表示参数
    Map<String, Object> selectProductAttributeByCategory(Long cid, Integer type, Integer pageNum, Integer pageSize);
}
