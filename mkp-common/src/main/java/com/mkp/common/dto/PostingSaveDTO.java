package com.mkp.common.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 发布单入参。
 * 没有 direction 字段：资金方向由 category 推导（寄养=发布者出钱，出售=买家付钱），
 * 不再让用户选"我需要/我提供"，避免同一个分类下出现两种相反的资金方向。
 */
public class PostingSaveDTO {
    private Integer category;
    private String petName;
    private String species;
    private String breed;
    private Integer ageMonth;
    private BigDecimal weightKg;
    private Integer gender;
    private BigDecimal price;
    private Integer priceUnit;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Integer durationMinutes;
    private String description;
    private List<String> images;

    public Integer getCategory() { return category; }
    public void setCategory(Integer category) { this.category = category; }
    public String getPetName() { return petName; }
    public void setPetName(String petName) { this.petName = petName; }
    public String getSpecies() { return species; }
    public void setSpecies(String species) { this.species = species; }
    public String getBreed() { return breed; }
    public void setBreed(String breed) { this.breed = breed; }
    public Integer getAgeMonth() { return ageMonth; }
    public void setAgeMonth(Integer ageMonth) { this.ageMonth = ageMonth; }
    public BigDecimal getWeightKg() { return weightKg; }
    public void setWeightKg(BigDecimal weightKg) { this.weightKg = weightKg; }
    public Integer getGender() { return gender; }
    public void setGender(Integer gender) { this.gender = gender; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Integer getPriceUnit() { return priceUnit; }
    public void setPriceUnit(Integer priceUnit) { this.priceUnit = priceUnit; }
    public LocalDateTime getStartDate() { return startDate; }
    public void setStartDate(LocalDateTime startDate) { this.startDate = startDate; }
    public LocalDateTime getEndDate() { return endDate; }
    public void setEndDate(LocalDateTime endDate) { this.endDate = endDate; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<String> getImages() { return images; }
    public void setImages(List<String> images) { this.images = images; }
}
