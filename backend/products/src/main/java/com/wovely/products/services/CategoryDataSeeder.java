package com.wovely.products.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import com.wovely.products.models.Category;
import com.wovely.products.repository.CategoryRepository;

@Component
public class CategoryDataSeeder implements CommandLineRunner {

    @Autowired
    CategoryRepository categoryRepository;

    @Override
    public void run(String... args) throws Exception {
        if (categoryRepository.count() == 0) {
            seedCategories();
        }
    }

    private void seedCategories() {
        // Roots
        Category homeLiving = categoryRepository.save(new Category("Home & Living", null, "home-living", 0));
        Category clothing = categoryRepository.save(new Category("Clothing", null, "clothing", 0));
        Category jewelry = categoryRepository.save(new Category("Jewelry", null, "jewelry", 0));
        Category art = categoryRepository.save(new Category("Art & Collectibles", null, "art", 0));

        // Sub-categories (Level 1)
        Category ceramics = categoryRepository.save(new Category("Ceramics", homeLiving.getId(), "ceramics", 1));
        Category decor = categoryRepository.save(new Category("Decor", homeLiving.getId(), "decor", 1));
        Category furniture = categoryRepository.save(new Category("Furniture", homeLiving.getId(), "furniture", 1));

        Category women = categoryRepository.save(new Category("Women", clothing.getId(), "women", 1));
        Category men = categoryRepository.save(new Category("Men", clothing.getId(), "men", 1));
        Category accessories = categoryRepository.save(new Category("Accessories", clothing.getId(), "accessories", 1));

        // Sub-categories (Level 2)
        categoryRepository.save(new Category("Mugs", ceramics.getId(), "mugs", 2));
        categoryRepository.save(new Category("Plates", ceramics.getId(), "plates", 2));
        categoryRepository.save(new Category("Vases", ceramics.getId(), "vases", 2));

        categoryRepository.save(new Category("Candles", decor.getId(), "candles", 2));
        categoryRepository.save(new Category("Wall Art", decor.getId(), "wall-art", 2));

        categoryRepository.save(new Category("Bags", accessories.getId(), "bags", 2));
        categoryRepository.save(new Category("Scarves", accessories.getId(), "scarves", 2));

        categoryRepository.save(new Category("Necklaces", jewelry.getId(), "necklaces", 2));
        categoryRepository.save(new Category("Earrings", jewelry.getId(), "earrings", 2));
    }
}
