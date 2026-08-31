# Herramientas de imagen

## `indexar-png.mjs` — optimizar el plano de la feria

Convierte un PNG RGBA de colores planos (un plano CAD) a **PNG indexado** de hasta 256
colores. Sin dependencias: solo `zlib` de Node.

```bash
node frontend/herramientas/indexar-png.mjs <entrada.png> <salida.png> [nColores]
```

### Resultado sobre el plano de FEXPO

| | Antes | Después |
|---|---|---|
| Tamaño | 1 268 860 B | **338 781 B** (−73,3 %) |
| Formato | RGBA 8 bits, sin paleta | Indexado, paleta de 256 |
| Error | — | RMSE 1,63 / 255 |

El original se conserva en `frontend/imagen-fuente/mapa-original.png` (fuera de `public/`,
así que **no se publica al navegador**) para poder reprocesarlo con otros parámetros.

### Por qué PNG indexado y no WebP ni JPEG

- **JPEG no sirve**: el plano tiene rótulos de ~8 px y líneas de 1 px. El DCT los emborrona
  y además ensucia el blanco, que aquí es el 88,5 % de la imagen.
- **WebP no se puede generar en esta máquina**: no hay `cwebp`, ni ImageMagick, ni ffmpeg,
  y .NET solo trae codificadores BMP/GIF/JPEG/PNG/TIFF. (Si algún día se instala `cwebp`,
  WebP sin pérdida debería bajar aún más — vale la pena volver a medirlo.)
- **El alfa sobraba**: el original era RGBA con alfa 100 % opaco, un canal entero de más.
- Los ~69 600 colores «únicos» del original son **antialias** alrededor de una treintena de
  colores reales, no información: por eso cuantizar casi no introduce error.

### No se redujo la resolución

Se mantiene 1836×2376 a propósito. El plano se usa dentro de un visor con zoom y tiene
texto diminuto (nombres de zona, numeración de casetas); reducirlo a ~1400 px lo volvería
ilegible justo cuando el vendedor lo amplía para enseñárselo al cliente.

### Verificar un cambio

`comparar.mjs` (en el scratchpad de la sesión) recorta la misma región de dos PNG y las
apila para comparar a tamaño real. La comprobación que se hizo aquí fue sobre la caja de
**REFERENCIA** (texto pequeño sobre colores planos) y sobre la zona densa de casetas
junto a la **TARIMA**: ambas quedaron indistinguibles del original.
