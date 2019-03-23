package com.atguigu.gmall.pms.service.impl;

import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.constant.RedisCacheConstant;
import com.atguigu.gmall.pms.entity.ProductCategory;
import com.atguigu.gmall.pms.mapper.ProductCategoryMapper;
import com.atguigu.gmall.pms.service.ProductCategoryService;
import com.atguigu.gmall.to.PmsProductCategoryWithChildrenItem;
import com.atguigu.gmall.utils.PageUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 产品分类 服务实现类
 * </p>
 *
 * @author Lfy
 * @since 2019-03-19
 */
@Slf4j
@Service
@Component
public class ProductCategoryServiceImpl extends ServiceImpl<ProductCategoryMapper, ProductCategory> implements ProductCategoryService {

    @Autowired
    StringRedisTemplate redisTemplate;

    @Override
    public Map<String, Object> pageCategory(Long parentId, Integer pageSize, Integer pageNum) {

        ProductCategoryMapper productCategoryMapper = getBaseMapper();

        QueryWrapper<ProductCategory> eq = null;
        if (!StringUtils.isEmpty(parentId)) {
            eq = new QueryWrapper<ProductCategory>().eq("parent_id", parentId);
        }
        IPage<ProductCategory> page = productCategoryMapper.selectPage(new Page<ProductCategory>(pageNum, pageSize), eq);
        //封装数据

        return PageUtils.getPageMap(page);

    }

    @Override
    public List<PmsProductCategoryWithChildrenItem> listWithChildren() {
        // 这个数据加缓存

        ValueOperations<String, String> ops = redisTemplate.opsForValue();

        String cache = ops.get(RedisCacheConstant.PRODUCT_CATEGORY_CACHE_KEY);
        if (!StringUtils.isEmpty(cache)) {
            log.debug("PRODUCT_CATEGORY_CACHE 缓存命中...");
            //转换过来
            List<PmsProductCategoryWithChildrenItem> items = JSON.parseArray(cache, PmsProductCategoryWithChildrenItem.class);
            return items;
        }

        log.debug("PRODUCT_CATEGORY_CACHE 缓存未命中，去数据库查询...");
        ProductCategoryMapper baseMapper = getBaseMapper();
        List<PmsProductCategoryWithChildrenItem> items = baseMapper.listWithChildren(0);


        //缓存数据都给一个过期时间比较好

        String jsonString = JSON.toJSONString(items);
        ops.set(RedisCacheConstant.PRODUCT_CATEGORY_CACHE_KEY, jsonString, 3, TimeUnit.DAYS);

        //查某个菜单的所有子菜单


        return items;
    }

}

