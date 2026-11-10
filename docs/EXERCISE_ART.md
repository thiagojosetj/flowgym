# Ilustrações dos exercícios — prompts

Gerado por `tools/exercise_art_prompts.py` a partir de `catalog.json`. Nome,
equipamento e músculo-alvo saem do próprio catálogo, então nenhum prompt pode
pedir um músculo que o app não associa àquele exercício. Se o catálogo mudar,
rode de novo em vez de editar à mão.

As imagens **não** são geradas por este repositório e não têm licença definida
aqui: quem gerar precisa conferir os termos da ferramenta usada antes de as
imagens entrarem num repositório público.

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

## Um pedido por exercício

São 55. Gere em lotes pequenos e confira a consistência
entre os lotes antes de seguir.

### Supino reto com barra

```
Exercício: Supino reto com barra.
Equipamento: Barra, Banco.
Momento: barra a meio caminho do peito, cotovelos a cerca de 90 graus.
Destaque azul em: Peitoral médio (porção esternal).
Arquivo: barbell_bench_press.png
```

### Supino inclinado com barra

```
Exercício: Supino inclinado com barra.
Equipamento: Barra, Banco.
Momento: banco inclinado a 30 graus, barra na altura da clavícula.
Destaque azul em: Peitoral superior (porção clavicular).
Arquivo: incline_barbell_bench_press.png
```

### Supino declinado com barra

```
Exercício: Supino declinado com barra.
Equipamento: Barra, Banco.
Momento: banco declinado, barra na altura da parte baixa do peito.
Destaque azul em: Peitoral inferior (porção abdominal).
Arquivo: decline_barbell_bench_press.png
```

### Supino reto com halteres

```
Exercício: Supino reto com halteres.
Equipamento: Halteres, Banco.
Momento: halteres na altura do peito, cotovelos abertos a 45 graus.
Destaque azul em: Peitoral médio (porção esternal).
Arquivo: dumbbell_bench_press.png
```

### Supino inclinado com halteres

```
Exercício: Supino inclinado com halteres.
Equipamento: Halteres, Banco.
Momento: banco inclinado, halteres na altura dos ombros.
Destaque azul em: Peitoral superior (porção clavicular).
Arquivo: incline_dumbbell_press.png
```

### Crucifixo com halteres

```
Exercício: Crucifixo com halteres.
Equipamento: Halteres, Banco.
Momento: braços abertos na lateral, cotovelos levemente flexionados.
Destaque azul em: Peitoral médio (porção esternal).
Arquivo: dumbbell_fly.png
```

### Crossover na polia alta

```
Exercício: Crossover na polia alta.
Equipamento: Polia (cabo).
Momento: mãos se encontrando à frente do corpo, polias altas atrás.
Destaque azul em: Peitoral inferior (porção abdominal).
Arquivo: cable_crossover.png
```

### Supino na máquina

```
Exercício: Supino na máquina.
Equipamento: Máquina.
Momento: sentado na máquina, braços quase estendidos à frente.
Destaque azul em: Peitoral médio (porção esternal).
Arquivo: machine_chest_press.png
```

### Voador (peck deck)

```
Exercício: Voador (peck deck).
Equipamento: Máquina.
Momento: sentado, braços fechados à frente na altura do peito.
Destaque azul em: Peitoral médio (porção esternal).
Arquivo: pec_deck.png
```

### Flexão de braços

```
Exercício: Flexão de braços.
Equipamento: Peso corporal.
Momento: corpo em prancha, cotovelos flexionados, peito perto do chão.
Destaque azul em: Peitoral médio (porção esternal).
Arquivo: push_up.png
```

### Mergulho nas paralelas

```
Exercício: Mergulho nas paralelas.
Equipamento: Paralelas.
Momento: nas paralelas, tronco inclinado à frente, cotovelos flexionados.
Destaque azul em: Peitoral inferior (porção abdominal).
Arquivo: chest_dip.png
```

### Barra fixa (pegada pronada)

```
Exercício: Barra fixa (pegada pronada).
Equipamento: Barra fixa.
Momento: queixo na altura da barra, pegada pronada, visto de costas.
Destaque azul em: Latíssimo do dorso.
Arquivo: pull_up.png
```

### Barra fixa supinada

```
Exercício: Barra fixa supinada.
Equipamento: Barra fixa.
Momento: queixo na altura da barra, pegada supinada, visto de costas.
Destaque azul em: Latíssimo do dorso.
Arquivo: chin_up.png
```

### Puxada frontal na polia

```
Exercício: Puxada frontal na polia.
Equipamento: Polia (cabo).
Momento: sentado, barra puxada até a altura do queixo.
Destaque azul em: Latíssimo do dorso.
Arquivo: lat_pulldown.png
```

### Remada baixa na polia

```
Exercício: Remada baixa na polia.
Equipamento: Polia (cabo).
Momento: sentado, punho puxado até o abdômen, costas eretas.
Destaque azul em: Latíssimo do dorso, Romboides.
Arquivo: seated_cable_row.png
```

### Remada curvada com barra

```
Exercício: Remada curvada com barra.
Equipamento: Barra.
Momento: tronco inclinado a 45 graus, barra puxada até o umbigo.
Destaque azul em: Latíssimo do dorso, Romboides.
Arquivo: barbell_row.png
```

### Remada unilateral com halter (serrote)

```
Exercício: Remada unilateral com halter (serrote).
Equipamento: Halteres, Banco.
Momento: um joelho e uma mão no banco, halter puxado até a lateral do tronco.
Destaque azul em: Latíssimo do dorso.
Arquivo: one_arm_dumbbell_row.png
```

### Remada cavalinho

```
Exercício: Remada cavalinho.
Equipamento: Máquina com anilhas.
Momento: tronco inclinado, pegada neutra, barra puxada até o abdômen.
Destaque azul em: Romboides, Latíssimo do dorso.
Arquivo: t_bar_row.png
```

### Pulldown com braços estendidos

```
Exercício: Pulldown com braços estendidos.
Equipamento: Polia (cabo).
Momento: em pé, braços estendidos empurrando a barra até as coxas.
Destaque azul em: Latíssimo do dorso.
Arquivo: straight_arm_pulldown.png
```

### Encolhimento com barra

```
Exercício: Encolhimento com barra.
Equipamento: Barra.
Momento: em pé, barra à frente das coxas, ombros elevados.
Destaque azul em: Trapézio superior.
Arquivo: barbell_shrug.png
```

### Face pull na polia

```
Exercício: Face pull na polia.
Equipamento: Polia (cabo).
Momento: corda puxada até a altura do rosto, cotovelos altos e abertos.
Destaque azul em: Deltoide posterior.
Arquivo: face_pull.png
```

### Desenvolvimento em pé com barra

```
Exercício: Desenvolvimento em pé com barra.
Equipamento: Barra.
Momento: em pé, barra travada acima da cabeça, braços estendidos.
Destaque azul em: Deltoide anterior.
Arquivo: overhead_press.png
```

### Desenvolvimento com halteres

```
Exercício: Desenvolvimento com halteres.
Equipamento: Halteres, Banco.
Momento: sentado, halteres acima da cabeça, braços estendidos.
Destaque azul em: Deltoide anterior.
Arquivo: dumbbell_shoulder_press.png
```

### Elevação lateral com halteres

```
Exercício: Elevação lateral com halteres.
Equipamento: Halteres.
Momento: em pé, braços abertos na linha dos ombros.
Destaque azul em: Deltoide lateral.
Arquivo: lateral_raise.png
```

### Elevação lateral unilateral na polia

```
Exercício: Elevação lateral unilateral na polia.
Equipamento: Polia (cabo).
Momento: em pé de lado para a polia baixa, um braço aberto na linha do ombro.
Destaque azul em: Deltoide lateral.
Arquivo: cable_lateral_raise.png
```

### Crucifixo inverso na máquina

```
Exercício: Crucifixo inverso na máquina.
Equipamento: Máquina.
Momento: sentado de frente para a máquina, braços abertos para trás.
Destaque azul em: Deltoide posterior.
Arquivo: rear_delt_machine_fly.png
```

### Elevação frontal com halteres

```
Exercício: Elevação frontal com halteres.
Equipamento: Halteres.
Momento: em pé, um braço elevado à frente na altura do ombro.
Destaque azul em: Deltoide anterior.
Arquivo: front_raise.png
```

### Rosca direta com barra

```
Exercício: Rosca direta com barra.
Equipamento: Barra.
Momento: em pé, barra na altura do peito, cotovelos junto ao tronco.
Destaque azul em: Bíceps — cabeça curta, Bíceps — cabeça longa.
Arquivo: barbell_curl.png
```

### Rosca alternada com halteres

```
Exercício: Rosca alternada com halteres.
Equipamento: Halteres.
Momento: em pé, um halter flexionado na altura do ombro e o outro estendido.
Destaque azul em: Bíceps — cabeça longa, Bíceps — cabeça curta.
Arquivo: dumbbell_curl.png
```

### Rosca martelo

```
Exercício: Rosca martelo.
Equipamento: Halteres.
Momento: em pé, pegada neutra, halter na altura do ombro.
Destaque azul em: Braquial, Braquiorradial.
Arquivo: hammer_curl.png
```

### Rosca Scott

```
Exercício: Rosca Scott.
Equipamento: Barra W, Banco.
Momento: sentado no banco Scott, braços apoiados, barra W na altura do peito.
Destaque azul em: Bíceps — cabeça curta.
Arquivo: preacher_curl.png
```

### Tríceps na polia

```
Exercício: Tríceps na polia.
Equipamento: Polia (cabo).
Momento: em pé na polia alta, cotovelos junto ao tronco, braços estendidos.
Destaque azul em: Tríceps — cabeça lateral.
Arquivo: triceps_pushdown.png
```

### Tríceps francês com halter

```
Exercício: Tríceps francês com halter.
Equipamento: Halteres.
Momento: halter atrás da cabeça, cotovelos apontando para cima.
Destaque azul em: Tríceps — cabeça longa.
Arquivo: overhead_triceps_extension.png
```

### Tríceps testa com barra W

```
Exercício: Tríceps testa com barra W.
Equipamento: Barra W, Banco.
Momento: deitado no banco, barra W descendo até a testa.
Destaque azul em: Tríceps — cabeça longa.
Arquivo: skull_crusher.png
```

### Mergulho no banco

```
Exercício: Mergulho no banco.
Equipamento: Banco.
Momento: mãos apoiadas no banco atrás do corpo, cotovelos flexionados.
Destaque azul em: Tríceps.
Arquivo: bench_dip.png
```

### Rosca de punho com barra

```
Exercício: Rosca de punho com barra.
Equipamento: Barra, Banco.
Momento: sentado, antebraços apoiados nas coxas, punhos flexionados para cima.
Destaque azul em: Flexores do punho.
Arquivo: wrist_curl.png
```

### Agachamento livre com barra

```
Exercício: Agachamento livre com barra.
Equipamento: Barra.
Momento: barra nas costas, coxas paralelas ao chão.
Destaque azul em: Quadríceps, Glúteo máximo.
Arquivo: back_squat.png
```

### Leg press 45°

```
Exercício: Leg press 45°.
Equipamento: Máquina com anilhas.
Momento: sentado no leg press 45 graus, joelhos flexionados perto do peito.
Destaque azul em: Quadríceps.
Arquivo: leg_press.png
```

### Cadeira extensora

```
Exercício: Cadeira extensora.
Equipamento: Máquina.
Momento: sentado na máquina, pernas estendidas à frente.
Destaque azul em: Reto femoral, Vasto lateral, Vasto medial.
Arquivo: leg_extension.png
```

### Mesa flexora

```
Exercício: Mesa flexora.
Equipamento: Máquina.
Momento: deitado de bruços, calcanhares puxados em direção aos glúteos.
Destaque azul em: Bíceps femoral, Semitendinoso e semimembranoso.
Arquivo: lying_leg_curl.png
```

### Cadeira flexora

```
Exercício: Cadeira flexora.
Equipamento: Máquina.
Momento: sentado, pernas flexionadas para baixo e para trás.
Destaque azul em: Semitendinoso e semimembranoso, Bíceps femoral.
Arquivo: seated_leg_curl.png
```

### Levantamento terra romeno (stiff)

```
Exercício: Levantamento terra romeno (stiff).
Equipamento: Barra.
Momento: em pé, tronco inclinado à frente, barra na altura dos joelhos, pernas quase estendidas.
Destaque azul em: Posteriores de coxa, Glúteo máximo.
Arquivo: romanian_deadlift.png
```

### Levantamento terra

```
Exercício: Levantamento terra.
Equipamento: Barra.
Momento: em pé, barra travada na altura do quadril, tronco ereto.
Destaque azul em: Glúteo máximo, Posteriores de coxa, Eretores da espinha.
Arquivo: deadlift.png
```

### Elevação pélvica com barra

```
Exercício: Elevação pélvica com barra.
Equipamento: Barra, Banco.
Momento: costas apoiadas no banco, quadril estendido no alto, barra sobre o quadril.
Destaque azul em: Glúteo máximo.
Arquivo: hip_thrust.png
```

### Agachamento búlgaro com halteres

```
Exercício: Agachamento búlgaro com halteres.
Equipamento: Halteres, Banco.
Momento: pé de trás apoiado no banco, joelho da frente a 90 graus.
Destaque azul em: Quadríceps, Glúteo máximo.
Arquivo: bulgarian_split_squat.png
```

### Avanço com halteres

```
Exercício: Avanço com halteres.
Equipamento: Halteres.
Momento: passo à frente, joelho de trás perto do chão, halteres ao lado do corpo.
Destaque azul em: Quadríceps, Glúteo máximo.
Arquivo: walking_lunge.png
```

### Cadeira adutora

```
Exercício: Cadeira adutora.
Equipamento: Máquina.
Momento: sentado na máquina, pernas fechando uma contra a outra.
Destaque azul em: Adutores.
Arquivo: hip_adduction_machine.png
```

### Cadeira abdutora

```
Exercício: Cadeira abdutora.
Equipamento: Máquina.
Momento: sentado na máquina, pernas abrindo para fora.
Destaque azul em: Glúteo médio, Glúteo mínimo.
Arquivo: hip_abduction_machine.png
```

### Panturrilha em pé na máquina

```
Exercício: Panturrilha em pé na máquina.
Equipamento: Máquina.
Momento: em pé na máquina, calcanhares elevados, na ponta dos pés.
Destaque azul em: Gastrocnêmio.
Arquivo: standing_calf_raise.png
```

### Panturrilha sentado

```
Exercício: Panturrilha sentado.
Equipamento: Máquina.
Momento: sentado com o apoio sobre as coxas, calcanhares elevados.
Destaque azul em: Sóleo.
Arquivo: seated_calf_raise.png
```

### Prancha abdominal

```
Exercício: Prancha abdominal.
Equipamento: Peso corporal.
Momento: apoiado nos antebraços e nas pontas dos pés, corpo em linha reta, visto de perfil.
Destaque azul em: Transverso do abdômen, Reto abdominal.
Arquivo: plank.png
```

### Abdominal supra

```
Exercício: Abdominal supra.
Equipamento: Peso corporal.
Momento: deitado de costas, joelhos flexionados, tronco levemente enrolado para cima.
Destaque azul em: Reto abdominal.
Arquivo: crunch.png
```

### Elevação de pernas na barra

```
Exercício: Elevação de pernas na barra.
Equipamento: Barra fixa.
Momento: pendurado na barra, pernas elevadas até a altura do quadril.
Destaque azul em: Reto abdominal.
Arquivo: hanging_leg_raise.png
```

### Abdominal na polia

```
Exercício: Abdominal na polia.
Equipamento: Polia (cabo).
Momento: ajoelhado de costas para a polia alta, tronco enrolado para baixo.
Destaque azul em: Reto abdominal.
Arquivo: cable_crunch.png
```

### Extensão lombar no banco romano

```
Exercício: Extensão lombar no banco romano.
Equipamento: Banco.
Momento: no banco romano, tronco alinhado com as pernas ao subir.
Destaque azul em: Eretores da espinha.
Arquivo: back_extension.png
```
