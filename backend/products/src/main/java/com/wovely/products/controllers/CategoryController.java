package com.wovely.products.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.wovely.products.models.Category;
import com.wovely.products.repository.CategoryRepository;
import java.util.List;

@CrossOrigin(origins = "http://localhost:4200", maxAge = 3600)
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

  @Autowired
  CategoryRepository categoryRepository;

  @GetMapping("/roots")
  public List<Category> getRootCategories() {
    return categoryRepository.findByParentId(null);
  }

  @GetMapping("/children/{parentId}")
  public List<Category> getSubCategories(@PathVariable String parentId) {
    return categoryRepository.findByParentId(parentId);
  }

  @GetMapping
  public List<Category> getAllCategories() {
    return categoryRepository.findAll();
  }

  @PostMapping
  public ResponseEntity<Category> createCategory(@RequestBody Category category) {
    Category _category = categoryRepository.save(new Category(
        category.getName(),
        category.getParentId(),
        category.getSlug(),
        category.getLevel()
    ));
    return ResponseEntity.ok(_category);
  }
}
