-- Categoría demo
INSERT INTO category (code, name_es, name_en, name_pt, color_hex)
VALUES ('TECH','Tecnología','Technology','Tecnologia','#1f77b4')
ON CONFLICT DO NOTHING;

-- Área demo
INSERT INTO map_area (name, type, polygon_geojson, centroid_x, centroid_y)
VALUES (
  'Stand A1 Innovación',
  'STAND',
  '{
     "type":"Polygon",
     "coordinates":[[
        [200,300],[200,380],[260,380],[260,300],[200,300]
     ]]
   }',
  230, 340
)
ON CONFLICT DO NOTHING;


-- Baño
INSERT INTO map_area (name,type,polygon_geojson,centroid_x,centroid_y) VALUES (
  'Baño Zona Norte','BAÑO',
  '{"type":"Polygon","coordinates":[[[100,120],[100,160],[130,160],[130,120],[100,120]]]}',
  115,140
);

-- Escenario
INSERT INTO map_area (name,type,polygon_geojson,centroid_x,centroid_y) VALUES (
  'Escenario Principal','ESCENARIO',
  '{"type":"Polygon","coordinates":[[[600,800],[600,950],[700,950],[700,800],[600,800]]]}',
  650,875
);
