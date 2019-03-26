package com.atguigu.gmall.pms.service.impl;

import com.alibaba.dubbo.config.annotation.Service;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.pms.mapper.*;
import com.atguigu.gmall.pms.service.ProductService;
import com.atguigu.gmall.to.PmsProductParam;
import com.atguigu.gmall.utils.PageUtils;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * <p>
 * 商品信息 服务实现类
 * </p>
 *
 * @author Lfy
 * @since 2019-03-19
 */
@Component
@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

    @Autowired
    ProductMapper productMapper;

    @Autowired
    ProductLadderMapper productLadderMapper;

    @Autowired
    ProductFullReductionMapper productFullReductionMapper;

    @Autowired
    MemberPriceMapper memberPriceMapper;

    @Autowired
    SkuStockMapper skuStockMapper;

    @Autowired
    ProductAttributeValueMapper productAttributeValueMapper;

    @Autowired
    ProductCategoryMapper productCategoryMapper;

    //spring 的所有组件全是单例，一定会出现线程安全问题
    //只要没有共享属性，一个要读，一个要改，就不会出现安全问题
    //线程安全问题都是读写不同步导致的

    ThreadLocal<Product> productThreadLocal = new ThreadLocal<Product>();

    @Override
    public Map<String, Object> pageProduct(Integer pageSize, Integer pageNum) {
        ProductMapper baseMapper = getBaseMapper();
        IPage<Product> page = baseMapper.selectPage(new Page<Product>(pageNum, pageSize), null);

        //封装数据
        return PageUtils.getPageMap(page);
    }

    /**
     * 事务的传播行为：
     * Propagation {
     * 【REQUIRED(0)】,此方法需要事务，如果没有就开新事务，如果之前已存在就用旧事务
     * SUPPORTS(1),支持：有事务用事务，没有不用
     * MANDATORY(2),强制要求： 必须在事务中运行，没有就报错
     * 【REQUIRES_NEW(3)】,需要新的：这个方法必须用一个新的事务来做，不用混用
     * NOT_SUPPORTED(4),不支持：此方法不能在事务中运行，如果有事务，暂停之前的事务；
     * NEVER(5),从不用事务，否则抛异常
     * NESTED(6);内嵌事务；还原点
     * <p>
     * <p>
     * REQUIRED【和大方法用一个事务】
     * REQUIRES_NEW【用一个新事务】
     * 异常机制还是异常机制
     *
     * @Transactional 一定不要标准在Controller
     * //AOP做的事务
     * //基于反射调用了
     */


    @Transactional(propagation = Propagation.REQUIRED)
    @Override
    public void create(PmsProductParam productParam) {
        ProductServiceImpl proxy = (ProductServiceImpl) AopContext.currentProxy();

        //保存商品
        proxy.saveBaseProductInfo(productParam);

        //2、保存商品的阶梯价格存到 pms_product_ladder
        proxy.saveProductLadder(productParam.getProductLadderList());

        //3、保存商品的满减价格存到 pms_product_full_reduction
        proxy.saveProductFullReduction(productParam.getProductFullReductionList());

        //4、保存商品的会员价格存到 pms_member_price
        proxy.saveMemberPrice(productParam.getMemberPriceList());

        //6、保存参数及自定义规格到 pms_product_attribute_value()
        proxy.saveProductAttributeValue(productParam.getProductAttributeValueList());

        //7、更新商品分类数目存
        proxy.updateProductCategoryCount();

    }
    //1、保存商品的基本信息存到 pms_product(将刚才保存的这个商品的自增id获取出来)
    @Transactional(propagation = Propagation.REQUIRED)
    public Long saveProduct(PmsProductParam productParam) {
        Product product = new Product();
        BeanUtils.copyProperties(productParam, product);
        int insert = productMapper.insert(product);

        //共享商品信息的基础数据
        //同线程共享数据
        productThreadLocal.set(product);
        return product.getId();
    }

    //2、保存商品的阶梯价格存到 pms_product_ladder
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveProductLadder(List<ProductLadder> list) {
        Product product = productThreadLocal.get();
        for (ProductLadder ladder : list) {
            ladder.setProductId(product.getId());
            productLadderMapper.insert(ladder);
        }


    }

    //3、保存商品的满减价格存到 pms_product_full_reduction
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveProductFullReduction(List<ProductFullReduction> list) {
        Product product = productThreadLocal.get();

        for (ProductFullReduction reduction : list) {
            reduction.setProductId(product.getId());
            productFullReductionMapper.insert(reduction);
        }
    }

    //4、保存商品的会员价格存到 pms_member_price
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveMemberPrice(List<MemberPrice> list) {
        Product product = productThreadLocal.get();

        list.forEach((mp) -> {
            mp.setProductId(product.getId());
            memberPriceMapper.insert(mp);
        });
//        for (MemberPrice member : list) {
//            member.setProductId(product.getId());
//            memberPriceMapper.insert(member);
//        }
    }

    //5、保存商品的sku库存到 pms_sku_stock (sku编码要自动生成)
    @Transactional(propagation = Propagation.REQUIRED)
    public void saveSkuStock(List<SkuStock> list) {
        Product product = productThreadLocal.get();

        //线程安全的，遍历修改 线程不安全
        AtomicReference<Integer> i = new AtomicReference<>(0);

        NumberFormat numberFormat = DecimalFormat.getNumberInstance();
        numberFormat.setMinimumIntegerDigits(2);
        numberFormat.setMaximumIntegerDigits(2);
        list.forEach(skuStock -> {
            //保存商品id
            skuStock.setProductId(product.getId());

            //SKU编码 商品id自增
            //skuStock.setSkuCode(); 两位数，不够补0
            String format = numberFormat.format(i.get());

            String code = "K_"+product.getId()+"_"+format;

            skuStock.setSkuCode(code);
            //自增
            i.set(i.get() + 1);

            skuStockMapper.insert(skuStock);
        });
    }

    //6、保存参数及自定义规格到 pms_product_attribute_value()
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveProductAttributeValue(List<ProductAttributeValue> list) {
        Product product = productThreadLocal.get();

        list.forEach((pav) -> {
            pav.setProductId(product.getId());
            productAttributeValueMapper.insert(pav);
        });
    }

    //7、更新商品分类数目存
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateProductCategoryCount() {
        Product product = productThreadLocal.get();
        Long id = product.getProductCategoryId();
        productCategoryMapper.updateCountById(id);

    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveBaseProductInfo(PmsProductParam productParam){
        ProductServiceImpl proxy = (ProductServiceImpl) AopContext.currentProxy();

        //1、保存商品的基本信息存到 pms_product(将刚才保存的这个商品的自增id获取出来)
        proxy.saveProduct(productParam);

        //5、保存商品的sku库存到 pms_sku_stock (sku编码要自动生成)
        proxy.saveSkuStock(productParam.getSkuStockList());

    }
}