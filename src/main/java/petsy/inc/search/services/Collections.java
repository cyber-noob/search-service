package petsy.inc.search.services;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum Collections {

    Pet_Collection("pet-collection", "https://fly.storage.tigris.dev/pethouse-puppy/collection/pet_collection.jpg"),

    Pet_Accessories("pet-accessories", "https://fly.storage.tigris.dev/pethouse-puppy/collection/pet_accessories.jpg"),

    Pet_Food("pet-food", "https://fly.storage.tigris.dev/pethouse-puppy/collection/pet_food.jpeg"),

    Pet_Grooming("pet-grooming", "https://fly.storage.tigris.dev/pethouse-puppy/collection/pet_spa.jpeg"),

    Pet_DayCare("pet-daycare", "https://fly.storage.tigris.dev/pethouse-puppy/collection/pet_daycare.jpeg"),

    Pet_HealthCare("pet-healthcare", "https://fly.storage.tigris.dev/pethouse-puppy/collection/pet_healthcare.jpg");

    private final String slug;

    private final String url;
}