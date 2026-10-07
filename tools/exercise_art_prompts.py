#!/usr/bin/env python3
"""Generates the image-generation prompts for the exercise illustrations.

    python3 tools/exercise_art_prompts.py [docs/EXERCISE_ART.md]

Names, equipment and the target muscle come from the catalogue itself, so a prompt can never name
a muscle the app does not link to that exercise. Only MOMENTS below is written by hand: the phase
of the movement each picture should show.
"""
import json
import os
import sys

CATALOG = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                       "..", "android-app", "app", "src", "main", "assets", "catalog",
                       "catalog.json")

# The instant each illustration freezes. Written per exercise because "peak contraction" is not
# the clearest picture for every movement: a plank has no contraction phase, and a deadlift reads
# better locked out than mid-pull.
MOMENTS = {
    "barbell_bench_press": "barra a meio caminho do peito, cotovelos a cerca de 90 graus",
    "incline_barbell_bench_press": "banco inclinado a 30 graus, barra na altura da clavícula",
    "decline_barbell_bench_press": "banco declinado, barra na altura da parte baixa do peito",
    "dumbbell_bench_press": "halteres na altura do peito, cotovelos abertos a 45 graus",
    "incline_dumbbell_press": "banco inclinado, halteres na altura dos ombros",
    "dumbbell_fly": "braços abertos na lateral, cotovelos levemente flexionados",
    "cable_crossover": "mãos se encontrando à frente do corpo, polias altas atrás",
    "machine_chest_press": "sentado na máquina, braços quase estendidos à frente",
    "pec_deck": "sentado, braços fechados à frente na altura do peito",
    "push_up": "corpo em prancha, cotovelos flexionados, peito perto do chão",
    "chest_dip": "nas paralelas, tronco inclinado à frente, cotovelos flexionados",
    "pull_up": "queixo na altura da barra, pegada pronada, visto de costas",
    "chin_up": "queixo na altura da barra, pegada supinada, visto de costas",
    "lat_pulldown": "sentado, barra puxada até a altura do queixo",
    "seated_cable_row": "sentado, punho puxado até o abdômen, costas eretas",
    "barbell_row": "tronco inclinado a 45 graus, barra puxada até o umbigo",
    "one_arm_dumbbell_row": "um joelho e uma mão no banco, halter puxado até a lateral do tronco",
    "t_bar_row": "tronco inclinado, pegada neutra, barra puxada até o abdômen",
    "straight_arm_pulldown": "em pé, braços estendidos empurrando a barra até as coxas",
    "barbell_shrug": "em pé, barra à frente das coxas, ombros elevados",
    "face_pull": "corda puxada até a altura do rosto, cotovelos altos e abertos",
    "overhead_press": "em pé, barra travada acima da cabeça, braços estendidos",
    "dumbbell_shoulder_press": "sentado, halteres acima da cabeça, braços estendidos",
    "lateral_raise": "em pé, braços abertos na linha dos ombros",
    "cable_lateral_raise": "em pé de lado para a polia baixa, um braço aberto na linha do ombro",
    "rear_delt_machine_fly": "sentado de frente para a máquina, braços abertos para trás",
    "front_raise": "em pé, um braço elevado à frente na altura do ombro",
    "barbell_curl": "em pé, barra na altura do peito, cotovelos junto ao tronco",
    "dumbbell_curl": "em pé, um halter flexionado na altura do ombro e o outro estendido",
    "hammer_curl": "em pé, pegada neutra, halter na altura do ombro",
    "preacher_curl": "sentado no banco Scott, braços apoiados, barra W na altura do peito",
    "triceps_pushdown": "em pé na polia alta, cotovelos junto ao tronco, braços estendidos",
    "overhead_triceps_extension": "halter atrás da cabeça, cotovelos apontando para cima",
    "skull_crusher": "deitado no banco, barra W descendo até a testa",
    "bench_dip": "mãos apoiadas no banco atrás do corpo, cotovelos flexionados",
    "wrist_curl": "sentado, antebraços apoiados nas coxas, punhos flexionados para cima",
    "back_squat": "barra nas costas, coxas paralelas ao chão",
    "leg_press": "sentado no leg press 45 graus, joelhos flexionados perto do peito",
    "leg_extension": "sentado na máquina, pernas estendidas à frente",
    "lying_leg_curl": "deitado de bruços, calcanhares puxados em direção aos glúteos",
    "seated_leg_curl": "sentado, pernas flexionadas para baixo e para trás",
    "romanian_deadlift": "em pé, tronco inclinado à frente, barra na altura dos joelhos, pernas "
                         "quase estendidas",
    "deadlift": "em pé, barra travada na altura do quadril, tronco ereto",
    "hip_thrust": "costas apoiadas no banco, quadril estendido no alto, barra sobre o quadril",
    "bulgarian_split_squat": "pé de trás apoiado no banco, joelho da frente a 90 graus",
    "walking_lunge": "passo à frente, joelho de trás perto do chão, halteres ao lado do corpo",
    "hip_adduction_machine": "sentado na máquina, pernas fechando uma contra a outra",
    "hip_abduction_machine": "sentado na máquina, pernas abrindo para fora",
    "standing_calf_raise": "em pé na máquina, calcanhares elevados, na ponta dos pés",
    "seated_calf_raise": "sentado com o apoio sobre as coxas, calcanhares elevados",
    "plank": "apoiado nos antebraços e nas pontas dos pés, corpo em linha reta, visto de perfil",
    "crunch": "deitado de costas, joelhos flexionados, tronco levemente enrolado para cima",
    "hanging_leg_raise": "pendurado na barra, pernas elevadas até a altura do quadril",
    "cable_crunch": "ajoelhado de costas para a polia alta, tronco enrolado para baixo",
    "back_extension": "no banco romano, tronco alinhado com as pernas ao subir",
}

STYLE = """\
## Bloco de estilo — cole UMA vez, antes de pedir qualquer imagem

```
Você vai gerar uma SÉRIE de ilustrações para um aplicativo de musculação.
A CONSISTÊNCIA entre todas é mais importante que o capricho de qualquer uma
isolada: elas vão aparecer lado a lado numa lista. Siga sem variar:

CORPO (a parte que mais sai errada — não improvise nada aqui)
- Figura humana estilizada tipo MANEQUIM: corpo liso, SEM NENHUMA ROUPA,
  sem regata, sem shorts, sem calçado.
- Cabeça completamente lisa e careca: sem cabelo, sem rosto, sem olhos,
  sem boca, sem orelhas definidas.
- Androgina, musculatura discreta, MESMAS PROPORÇÕES em todas as imagens.
- Mãos simplificadas, sem dedos individuais desenhados.

ESTILO
- Ilustração vetorial plana, contorno escuro fino e uniforme.
- Sem sombreamento realista, sem gradiente, sem textura, sem brilho.
- Corpo em cinza-azulado neutro; equipamento em cinza escuro;
  UMA cor azul usada SÓ no músculo trabalhado.
- O destaque azul deve ter o FORMATO ANATÔMICO do músculo, acompanhando a
  fibra. Não é mancha, não é bolha, não é adesivo colado por cima.

ENQUADRAMENTO
- Quadrado 1:1, figura inteira centralizada, margem de respiro em volta.
- Fundo TOTALMENTE TRANSPARENTE: sem chão, sem sombra projetada, sem parede,
  sem cenário de academia, sem retângulo de cor por baixo.

PROIBIDO
- Qualquer texto, número, letra, seta, legenda, logo, marca ou assinatura.
- Fotorrealismo, anime, 3D renderizado, aquarela.
- Moldura, borda, cartão ou qualquer elemento de interface.

SAÍDA
- PNG com canal alpha, 1024x1024.
- Um arquivo por exercício, nomeado exatamente com o código indicado.
```
"""


def main(destination):
    with open(CATALOG, encoding="utf-8") as handle:
        catalog = json.load(handle)

    muscles = {}
    for group in catalog["muscles"]:
        muscles[group["code"]] = group["name"]
        for sub in group.get("subgroups", []):
            muscles[sub["code"]] = sub["name"]
    equipment = {item["code"]: item["name"] for item in catalog["equipment"]}

    lines = ["# Ilustrações dos exercícios — prompts",
             "",
             "Gerado por `tools/exercise_art_prompts.py` a partir de `catalog.json`. Nome,",
             "equipamento e músculo-alvo saem do próprio catálogo, então nenhum prompt pode",
             "pedir um músculo que o app não associa àquele exercício. Se o catálogo mudar,",
             "rode de novo em vez de editar à mão.",
             "",
             "As imagens **não** são geradas por este repositório e não têm licença definida",
             "aqui: quem gerar precisa conferir os termos da ferramenta usada antes de as",
             "imagens entrarem num repositório público.",
             "",
             STYLE,
             "## Um pedido por exercício",
             "",
             f"São {len(catalog['exercises'])}. Gere em lotes pequenos e confira a consistência",
             "entre os lotes antes de seguir.",
             ""]

    missing = []
    for exercise in catalog["exercises"]:
        code = exercise["code"]
        if code not in MOMENTS:
            missing.append(code)
            continue
        primary = ", ".join(muscles.get(m, m) for m in exercise.get("primary", []))
        gear = ", ".join(equipment.get(e, e) for e in exercise.get("equipment", []))
        lines += [f"### {exercise['name']}", "",
                  "```",
                  f"Exercício: {exercise['name']}.",
                  f"Equipamento: {gear}.",
                  f"Momento: {MOMENTS[code]}.",
                  f"Destaque azul em: {primary}.",
                  f"Arquivo: {code}.png",
                  "```", ""]

    if missing:
        raise SystemExit("sem 'momento' escrito para: " + ", ".join(missing))

    with open(destination, "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines))
    print(f"{destination}: {len(catalog['exercises'])} exercícios")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "docs/EXERCISE_ART.md")
