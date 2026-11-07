package com.wovely.products.controllers;

import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.wovely.common.error.ApiException;
import com.wovely.products.models.Category;
import com.wovely.products.repository.CategoryRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Category tree. Reads are public; writes are admin-only (security config). */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

  /** {@code slug} is derived from the name when omitted; {@code level} from the parent. */
  public record CreateCategoryRequest(
      @NotBlank @Size(max = 80) String name,
      String parentId,
      @Size(max = 80) @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*", message = "lowercase letters, digits and dashes") String slug) {
  }

  private final CategoryRepository categoryRepository;

  public CategoryController(CategoryRepository categoryRepository) {
    this.categoryRepository = categoryRepository;
  }

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
  @ResponseStatus(HttpStatus.CREATED)
  public Category createCategory(@Valid @RequestBody CreateCategoryRequest r) {
    int level = 0;
    if (r.parentId() != null) {
      Category parent = categoryRepository.findById(r.parentId())
          .orElseThrow(() -> ApiException.badRequest("Parent category does not exist"));
      level = (parent.getLevel() == null ? 0 : parent.getLevel()) + 1;
    }
    String slug = r.slug() != null ? r.slug() : slugify(r.name());
    if (categoryRepository.findBySlug(slug) != null) {
      throw ApiException.conflict("A category with slug '" + slug + "' already exists");
    }
    return categoryRepository.save(new Category(r.name().trim(), r.parentId(), slug, level));
  }

  static String slugify(String name) {
    String s = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    return s.isEmpty() ? "category" : s;
  }
}
