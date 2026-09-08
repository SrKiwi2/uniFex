-- V13: el plano de la feria deja de estar dentro del bundle y pasa a la base.
--
-- Hasta aqui la SPA cargaba /mapa.png, un archivo empaquetado por Vite. Eso significaba que
-- cambiar el plano obligaba a recompilar el APK y reinstalarlo en CADA telefono, y ademas
-- ataba el sistema a FEXPO: otra actividad, otro plano, otra compilacion.
--
-- Se guarda por edicion (cada feria tiene el suyo) y con las medidas de la imagen, porque el
-- visor necesita la PROPORCION para encuadrar: hoy `PanZoom` la lleva escrita a mano
-- (2376/1836) y con un plano nuevo ese numero deja de ser cierto.
--
-- `plano_version` es un contador que sube en cada reemplazo. Viaja en la URL (?v=) para
-- romper la cache: sin el, el WebView del APK seguiria mostrando el plano viejo aunque el
-- servidor ya tenga el nuevo, y no hay forma de pedirle al vendedor que "limpie la cache".
--
-- OJO con las coordenadas: puesto.mapa_x / mapa_y son fracciones 0..1 de la imagen. Si el
-- plano nuevo tiene OTRO encuadre, las casetas ya colocadas quedan descolocadas (la misma
-- fraccion cae en otro sitio). Cambiar solo la resolucion, con el mismo encuadre, las respeta.

ALTER TABLE edicion ADD COLUMN IF NOT EXISTS plano_archivo   VARCHAR(300);
ALTER TABLE edicion ADD COLUMN IF NOT EXISTS plano_ancho     INTEGER;
ALTER TABLE edicion ADD COLUMN IF NOT EXISTS plano_alto      INTEGER;
ALTER TABLE edicion ADD COLUMN IF NOT EXISTS plano_version   INTEGER NOT NULL DEFAULT 0;
ALTER TABLE edicion ADD COLUMN IF NOT EXISTS plano_subido_en TIMESTAMP;

COMMENT ON COLUMN edicion.plano_archivo IS 'Ruta relativa bajo app.upload-root, servida en /files/**. NULL = se usa el plano empaquetado de respaldo';
COMMENT ON COLUMN edicion.plano_ancho   IS 'Ancho en pixeles de la imagen: el visor deriva de aqui la proporcion del encuadre';
COMMENT ON COLUMN edicion.plano_alto    IS 'Alto en pixeles de la imagen';
COMMENT ON COLUMN edicion.plano_version IS 'Sube en cada reemplazo; viaja en la URL como ?v= para invalidar la cache del APK';
