package com.mkp.common.dto;

public class PetServiceAskDTO {
    private String species;
    private String breed;
    private Integer ageMonth;
    private Double weightKg;
    private Integer gender;
    private String extra;

    public String getSpecies() { return species; }
    public void setSpecies(String species) { this.species = species; }
    public String getBreed() { return breed; }
    public void setBreed(String breed) { this.breed = breed; }
    public Integer getAgeMonth() { return ageMonth; }
    public void setAgeMonth(Integer ageMonth) { this.ageMonth = ageMonth; }
    public Double getWeightKg() { return weightKg; }
    public void setWeightKg(Double weightKg) { this.weightKg = weightKg; }
    public Integer getGender() { return gender; }
    public void setGender(Integer gender) { this.gender = gender; }
    public String getExtra() { return extra; }
    public void setExtra(String extra) { this.extra = extra; }
}
