/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.vladmihalcea;

import com.vladmihalcea.hibernate.model.store.*;
import org.jooq.DSLContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static com.vladmihalcea.jooq.schema.Tables.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:spring/applicationContext-test.xml"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class HibernateCriteriaTest {

    private static final Logger LOG = LoggerFactory.getLogger(HibernateCriteriaTest.class);

    @PersistenceContext(unitName = "persistenceUnit")
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private DSLContext jooqContext;

    @Before
    public void beforeTest() {
        CleanDbUtil.cleanStore(transactionTemplate, entityManager);
    }

    @Test
    public void test() {
        transactionTemplate.execute(new TransactionCallback<Void>() {
            @Override
            public Void doInTransaction(TransactionStatus transactionStatus) {
                Company company = new Company();
                company.setName("TV Company");
                entityManager.persist(company);

                Product product1 = new Product("tvCode");
                product1.setName("TV");
                product1.setCompany(company);

                Image frontImage1 = new Image();
                frontImage1.setName("front image 1");
                frontImage1.setIndex(0);

                Image sideImage1 = new Image();
                sideImage1.setName("side image 1");
                sideImage1.setIndex(1);

                product1.addImage(frontImage1);
                product1.addImage(sideImage1);

                WarehouseProductInfo warehouseProductInfo1 = new WarehouseProductInfo();
                warehouseProductInfo1.setQuantity(101);
                product1.addWarehouse(warehouseProductInfo1);

                Product product2 = new Product("tcSetCode");
                product2.setName("TV Set");
                product2.setCompany(company);

                Image frontImage2 = new Image();
                frontImage2.setName("front image 2");
                frontImage2.setIndex(2);

                Image sideImage2 = new Image();
                sideImage2.setName("side image 2");
                sideImage2.setIndex(3);

                product2.addImage(frontImage2);
                product2.addImage(sideImage2);

                WarehouseProductInfo warehouseProductInfo2 = new WarehouseProductInfo();
                warehouseProductInfo2.setQuantity(55);
                product2.addWarehouse(warehouseProductInfo2);

                Product product3 = new Product("cdPlayerCode");
                product3.setName("CD Player");
                product3.setCompany(company);

                WarehouseProductInfo warehouseProductInfo3 = new WarehouseProductInfo();
                warehouseProductInfo3.setQuantity(11);
                product3.addWarehouse(warehouseProductInfo3);

                Product product = entityManager.find(Product.class, 1L);
                //product.setQuantity(10);

                entityManager.persist(product1);
                entityManager.persist(product2);
                entityManager.persist(product3);
                return null;
            }
        });

        assertProducts(getProducts_Mercilessly());
        assertProducts(getProducts_Mercifully());
        assertProducts(getProducts_Gracefully());

        assertNotNull(getProduct_Mercilessly());
        assertNotNull(getProduct_Mercifully());
        assertNotNull(getProduct_Gracefully());

        assertImageProductDTOs(getImageProductDTOs());
        assertImageProductDTOs(getImageProductDTOs_JOOQ());
    }

    private void assertProducts(List<Product> products) {
        assertEquals(2, products.size());
        assertEquals("TV", products.get(0).getName());
        assertEquals("TV Set", products.get(1).getName());
    }

    private void assertImageProductDTOs(List<ImageProductDTO> imageProductDTOs) {
        assertEquals(3, imageProductDTOs.size());
        int index = 0;
        assertEquals("front image 2", imageProductDTOs.get(index).getImageName());
        assertEquals("TV Set", imageProductDTOs.get(index).getProductName());
        index++;
        assertEquals("side image 1", imageProductDTOs.get(index).getImageName());
        assertEquals("TV", imageProductDTOs.get(index).getProductName());
        index++;
        assertEquals("side image 2", imageProductDTOs.get(index).getImageName());
        assertEquals("TV Set", imageProductDTOs.get(index).getProductName());
    }

    private List<Product> getProducts_Mercilessly() {
        return transactionTemplate.execute(new TransactionCallback<List<Product>>() {
            @Override
            public List<Product> doInTransaction(TransactionStatus transactionStatus) {
                CriteriaBuilder cb = entityManager.getCriteriaBuilder();
                CriteriaQuery<Product> query = cb.createQuery(Product.class);
                Root<Product> product = query.from(Product.class);
                query.select(product);
                query.distinct(true);

                List<Predicate> criteria = new ArrayList<Predicate>();
                criteria.add(cb.like(cb.lower(product.get(Product_.name)), "%tv%"));

                Subquery<Long> subQuery = query.subquery(Long.class);
                Root<Image> infoRoot = subQuery.from(Image.class);
                Join<Image, Product> productJoin = infoRoot.join(Image_.product);
                subQuery.select(productJoin.<Long>get(Product_.id));

                subQuery.where(cb.gt(infoRoot.get(Image_.index), 0));
                criteria.add(cb.in(product.get(Product_.id)).value(subQuery));
                query.where(cb.and(criteria.toArray(new Predicate[criteria.size()])));
                return entityManager.createQuery(query).getResultList();
            }
        });
    }

    private List<Product> getProducts_Mercifully() {
        return transactionTemplate.execute(new TransactionCallback<List<Product>>() {
            @Override
            public List<Product> doInTransaction(TransactionStatus transactionStatus) {
                CriteriaBuilder cb = entityManager.getCriteriaBuilder();
                CriteriaQuery<Product> query = cb.createQuery(Product.class);
                Root<Image> imageRoot = query.from(Image.class);
                Join<Image, Product> productJoin = imageRoot.join(Image_.product);
                query.select(productJoin);
                query.distinct(true);
                List<Predicate> criteria = new ArrayList<Predicate>();
                criteria.add(cb.like(cb.lower(productJoin.get(Product_.name)), "%tv%"));
                criteria.add(cb.gt(imageRoot.get(Image_.index), 0));
                query.where(cb.and(criteria.toArray(new Predicate[criteria.size()])));
                return entityManager.createQuery(query).getResultList();
            }
        });
    }

    private List<Product> getProducts_Gracefully() {
        return transactionTemplate.execute(new TransactionCallback<List<Product>>() {
            @Override
            public List<Product> doInTransaction(TransactionStatus transactionStatus) {
                return entityManager.createQuery(
                        "select distinct p " +
                                "from Image i " +
                                "inner join i.product p " +
                                "where " +
                                "   lower(p.name) like :name and " +
                                "   i.index > :index ", Product.class)
                        .setParameter("name", "%tv%")
                        .setParameter("index", 0)
                        .getResultList();
            }
        });
    }

    private Product getProduct_Mercilessly() {
        return transactionTemplate.execute(new TransactionCallback<Product>() {
            @Override
            public Product doInTransaction(TransactionStatus transactionStatus) {
                CriteriaBuilder cb = entityManager.getCriteriaBuilder();
                CriteriaQuery<Product> query = cb.createQuery(Product.class);
                Root<Product> productRoot = query.from(Product.class);

                query.select(productRoot)
                        .where(cb.and(cb.equal(productRoot.get(Product_.code), "tvCode"),
                                cb.gt(productRoot.get(Product_.warehouseProductInfo).get(WarehouseProductInfo_.quantity), 50)));
                return entityManager.createQuery(query).getSingleResult();
            }
        });
    }

    private Product getProduct_Mercifully() {
        return transactionTemplate.execute(new TransactionCallback<Product>() {
            @Override
            public Product doInTransaction(TransactionStatus transactionStatus) {
                CriteriaBuilder cb = entityManager.getCriteriaBuilder();
                CriteriaQuery<Product> query = cb.createQuery(Product.class);
                Root<Product> productRoot = query.from(Product.class);
                Join<Product, WarehouseProductInfo> warehouseProductInfoJoin = productRoot.join(Product_.warehouseProductInfo);

                query.select(productRoot)
                        .where(cb.and(cb.equal(productRoot.get(Product_.code), "tvCode"),
                                cb.gt(warehouseProductInfoJoin.get(WarehouseProductInfo_.quantity), 50)));
                return entityManager.createQuery(query).getSingleResult();
            }
        });
    }


    private Product getProduct_Gracefully() {
        return transactionTemplate.execute(new TransactionCallback<Product>() {
            @Override
            public Product doInTransaction(TransactionStatus transactionStatus) {
                return entityManager.createQuery(
                        "select p " +
                                "from Product p " +
                                "inner join p.warehouseProductInfo w " +
                                "where " +
                                "   p.code = :code and " +
                                "   w.quantity > :quantity ", Product.class)
                        .setParameter("code", "tvCode")
                        .setParameter("quantity", 50)
                        .getSingleResult();
            }
        });
    }

    private List<ImageProductDTO> getImageProductDTOs() {
        return transactionTemplate.execute(new TransactionCallback<List<ImageProductDTO>>() {
            @Override
            public List<ImageProductDTO> doInTransaction(TransactionStatus transactionStatus) {
                CriteriaBuilder cb = entityManager.getCriteriaBuilder();
                CriteriaQuery<ImageProductDTO> query = cb.createQuery(ImageProductDTO.class);
                Root<Image> imageRoot = query.from(Image.class);
                Join<Image, Product> productJoin = imageRoot.join(Image_.product);
                query.distinct(true);
                List<Predicate> criteria = new ArrayList<Predicate>();
                criteria.add(cb.like(cb.lower(productJoin.get(Product_.name)), "%tv%"));
                criteria.add(cb.gt(imageRoot.get(Image_.index), 0));
                query.where(cb.and(criteria.toArray(new Predicate[criteria.size()])));
                query.select(cb.construct(ImageProductDTO.class, imageRoot.get(Image_.name), productJoin.get(Product_.name)))
                        .orderBy(cb.asc(imageRoot.get(Image_.name)));
                return entityManager.createQuery(query).getResultList();
            }
        });
    }

    private List<ImageProductDTO> getImageProductDTOs_JOOQ() {
        return transactionTemplate.execute(new TransactionCallback<List<ImageProductDTO>>() {
            @Override
            public List<ImageProductDTO> doInTransaction(TransactionStatus transactionStatus) {
                return jooqContext
                        .select(IMAGE.NAME, PRODUCT.NAME)
                        .from(IMAGE)
                        .join(PRODUCT).on(IMAGE.PRODUCT_ID.equal(PRODUCT.ID))
                        .where(PRODUCT.NAME.likeIgnoreCase("%tv%"))
                        .and(IMAGE.INDEX.greaterThan(0))
                        .orderBy(IMAGE.NAME.asc())
                        .fetch().into(ImageProductDTO.class);
            }
        });
    }
}
