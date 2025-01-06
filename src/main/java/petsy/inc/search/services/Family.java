package petsy.inc.search.services;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum Family {

    Dog("dog-puppy"),

    Cat("cat");

    private final String slug;
}
