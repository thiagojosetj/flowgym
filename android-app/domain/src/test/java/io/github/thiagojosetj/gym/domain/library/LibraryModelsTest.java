package io.github.thiagojosetj.gym.domain.library;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class LibraryModelsTest {

    @Test
    public void hierarchyIsBuiltFromAnUnorderedFlatList() {
        List<MuscleNode> nodes = Arrays.asList(
                new MuscleNode("s2", "back.traps", "Trapézio", "g2", 4),
                new MuscleNode("g2", "back", "Costas", null, 2),
                new MuscleNode("s1", "back.lats", "Latíssimo", "g2", 3),
                new MuscleNode("g1", "chest", "Peito", null, 0),
                new MuscleNode("orphan", "x.y", "Órfão", "missing", 1));

        List<MuscleGroup> groups = MuscleGroup.fromNodes(nodes);

        assertEquals(2, groups.size());
        assertEquals("Peito", groups.get(0).group().name());
        assertTrue(groups.get(0).subgroups().isEmpty());
        assertEquals("Costas", groups.get(1).group().name());
        assertEquals(Arrays.asList("s1", "s2"),
                Arrays.asList(groups.get(1).subgroups().get(0).id(), groups.get(1).subgroups().get(1).id()));
    }

    @Test
    public void qualifiedNameShowsTheGroupOnlyForSubgroups() {
        assertEquals("Costas · Latíssimo do dorso",
                new MuscleLink("m", "Latíssimo do dorso", "Costas").qualifiedName());
        assertEquals("Tríceps", new MuscleLink("m", "Tríceps", null).qualifiedName());
    }

    @Test
    public void instructionsAreSplitIntoTrimmedNonBlankSteps() {
        assertEquals(Arrays.asList("Deite no banco.", "Desça a barra.", "Empurre."),
                ExerciseDetail.splitSteps(" Deite no banco.\r\n\nDesça a barra.\n  Empurre.  \n"));
        assertTrue(ExerciseDetail.splitSteps(null).isEmpty());
    }
}
