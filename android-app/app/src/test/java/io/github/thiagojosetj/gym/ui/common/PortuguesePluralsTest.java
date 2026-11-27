package io.github.thiagojosetj.gym.ui.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.res.Resources;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import io.github.thiagojosetj.gym.R;
import io.github.thiagojosetj.gym.testutil.TestGymApplication;

/**
 * As duas regras do CLDR para português que já custaram texto errado neste app.
 *
 * <p>Elas não são sobre tradução: são sobre o app afirmar uma quantidade. Um plural em que a forma
 * escolhida não carrega o número é uma frase que diz um valor que ninguém passou, e é exatamente o
 * que o lint chama de {@code ImpliedQuantity}.
 */
@RunWith(AndroidJUnit4.class)
@Config(application = TestGymApplication.class, qualifiers = "pt-rBR-w411dp-h891dp")
public class PortuguesePluralsTest {

    @Test
    public void zeroCaiNaFormaDoSingularEAindaAssimPrecisaDizerZero() {
        Resources res = ApplicationProvider.getApplicationContext().getResources();

        // Em português o CLDR põe 0 e 1 na MESMA categoria ("one"). Uma forma "one" com o número
        // escrito à mão - era "1 etapa de drop-set também será resolvida." - afirma "1" quando
        // recebe zero. O chamador hoje filtra o zero, mas a string não pode depender disso.
        assertEquals("0 etapa de drop-set também será resolvida.",
                res.getQuantityString(R.plurals.finish_segments_resolved, 0, 0));
        assertEquals("Ver 0 série",
                res.getQuantityString(R.plurals.history_comparison_show_sets, 0, 0));

        // E com um, o texto é o mesmo de antes.
        assertEquals("1 etapa de drop-set também será resolvida.",
                res.getQuantityString(R.plurals.finish_segments_resolved, 1, 1));
        assertEquals("Ver 1 série",
                res.getQuantityString(R.plurals.history_comparison_show_sets, 1, 1));
    }

    @Test
    public void umMilhaoTemCategoriaPropriaEmPortuguesPorCausaDoDe() {
        Resources res = ApplicationProvider.getApplicationContext().getResources();

        // A categoria "many" do pt existe para múltiplos exatos de um milhão, porque o numeral
        // passa a pedir "de": "1.000.000 DE séries". É por isso que declarar tools:locale faz o
        // lint exigir um item "many" em todos os plurais - a exigência é gramática, não burocracia.
        String muitos = res.getQuantityString(R.plurals.template_set_count, 1_000_000, 1_000_000);
        assertTrue("esperava a forma com \"de\", veio: " + muitos, muitos.endsWith(" de séries"));

        // Três séries continuam três séries.
        assertEquals("3 séries", res.getQuantityString(R.plurals.template_set_count, 3, 3));
    }
}
