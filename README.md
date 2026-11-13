# Foco TDAH

App Android de agenda e foco para quem tem TDAH.

- **Agenda**: lista de tarefas por dia, com horário opcional; é só tocar para riscar.
- **Lembretes**: aviso na hora de cada tarefa, hora de planejar o dia seguinte, resumo da manhã e lembretes do que falta.
- **Foco**: mostra só a próxima tarefa, com cronômetro de 10 a 45 minutos.
- **Bloqueio**: mede o tempo nas redes sociais e, passado o tempo livre do dia, bloqueia os apps escolhidos até a lista de hoje estar concluída.
- Modo escuro (Ajustes → Aparência).
- Sequência de dias com a lista completa e liberação de emergência (5 min, 2 vezes por dia).

Os dados ficam só no aparelho.

## Instalar

Baixe `FocoTDAH.apk` em **Releases** pelo celular e toque para instalar. Depois, na aba Bloqueio, ative as duas permissões.

## Gerar o APK

Cada envio para a branch `main` gera o APK pelo GitHub Actions (`.github/workflows/build.yml`).
Localmente: `gradle assembleRelease` (Android SDK 34, JDK 17).

A interface fica em `app/src/main/assets/index.html`; a parte nativa (alarmes, uso, bloqueio) em `app/src/main/java/com/foco/tdah`.
