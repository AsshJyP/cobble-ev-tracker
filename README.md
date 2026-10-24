# Cobble EV Tracker

Mod **solo de cliente** para Minecraft **1.21.1** con **Cobblemon 1.7.3 o posterior** (probado hasta 1.8.2), para **Fabric** y **NeoForge**.

- HUD de entrenamiento de EVs: barras por stat y total sobre 510. Sigue al Pokémon que seleccionas (o a uno fijo) y se mueve y escala con el ratón.
- Objetivos por Pokémon, con un reparto **recomendado (★)** según sus stats base (pensando en su evolución final) y plantillas 252/252/4. Avisa al cumplir una stat, al pasarte y al completar el entrenamiento.
- **Resumen al terminar cada combate** con los EVs que ganó cada Pokémon del equipo. Lo de fuera de combate (vitaminas, bayas) se avisa al momento. También se puede elegir un aviso por cada cambio, las dos cosas o nada.
- **EVs que da cada especie en la Pokédex**: un "+N" junto a cada barra de stats base, con tooltip.
- Opcional: los EVs que da el salvaje contra el que combates.
- Menú de opciones con la tecla **N**, desde Mod Menu (Fabric) o desde el botón "Config" de la lista de mods (NeoForge).

Solo usa tus propios Pokémon (los EVs que ya ves en su resumen) y datos públicos de especie. No escanea el mundo ni muestra datos de Pokémon ajenos.

- Fabric: Fabric Loader ≥ 0.16.0, Fabric API y Cobblemon ≥ 1.7.3. Recomendado: Mod Menu.
- NeoForge: NeoForge 21.1.x y Cobblemon ≥ 1.7.3.
- Licencia: MIT. Pokémon es una marca de Nintendo/Game Freak/The Pokémon Company.

## Compilar
```
.\gradlew build
```
Los jars quedan en `fabric/build/libs/` y `neoforge/build/libs/` (el de cada loader; no el `-sources`).

Estructura: `common/` (código compartido), `fabric/` y `neoforge/` (lo propio de cada loader), al estilo de MultiLoader-Template.
