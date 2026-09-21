package io.github.thiagojosetj.gym.domain.util;

/** Source of new ids. Production uses {@link Ids#newId()}; tests inject predictable ids. */
@FunctionalInterface
public interface IdGenerator {

    IdGenerator UUID_V7 = Ids::newId;

    String newId();
}
