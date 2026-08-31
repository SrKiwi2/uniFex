-- Fase 3: figura del marcador de una categoria en el mapa (cuadrado/circulo/triangulo).
ALTER TABLE public.categoria ADD COLUMN IF NOT EXISTS forma varchar(20);
