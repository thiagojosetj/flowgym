package io.github.thiagojosetj.gym.data.local;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SqlLikeTest {

    @Test
    public void escapesWildcardsAndTheEscapeCharacter() {
        assertEquals("100\\%", SqlLike.escape("100%"));
        assertEquals("a\\_b", SqlLike.escape("a_b"));
        assertEquals("c\\\\d", SqlLike.escape("c\\d"));
        assertEquals("supino", SqlLike.escape("supino"));
    }
}
