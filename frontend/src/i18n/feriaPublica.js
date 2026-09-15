/**
 * Textos de la vista pública de la feria (/feria) en español y portugués.
 *
 * Portugués de Brasil a propósito: Cobija es frontera con Brasil (Brasiléia/Epitaciolândia,
 * en Acre) y buena parte del público que cruza lee portugués.
 *
 * Solo la interfaz. Lo que escribe el panel de administración —títulos y descripciones de las
 * noches, nombres de artistas y de categorías— se muestra tal como se guardó.
 *
 * Para añadir un texto: la MISMA clave en los dos idiomas. Si falta en uno, la vista mostraría
 * "undefined" justo en esa etiqueta.
 */

export const IDIOMAS = [
  { codigo: 'es', etiqueta: 'ES', nombre: 'Español', locale: 'es-BO' },
  { codigo: 'pt', etiqueta: 'PT', nombre: 'Português', locale: 'pt-BR' },
];

export const TEXTOS = {
  es: {
    nav: {
      secciones: 'Secciones de la feria',
      inicio: 'Inicio',
      artistas: 'Artistas',
      stands: 'Stands',
      obtenStand: 'Obtén tu stand',
      idioma: 'Idioma',
      temaClaro: 'Cambiar a tema claro',
      temaOscuro: 'Cambiar a tema oscuro',
      abrirMenu: 'Abrir menú de navegación',
    },
    hero: {
      comienzaEn: 'Comienza en',
      dias: 'días',
      horas: 'horas',
      min: 'min',
      seg: 'seg',
      yaComenzo: '¡La FEXPO UAP ya comenzó!',
      lugar: '18 de septiembre · Cobija, Pando',
      verPlano: 'Ver el plano de la feria',
      quieroExponer: 'Quiero exponer',
    },
    noches: {
      titulo: 'Noches de FEXPO',
      subtitulo: 'Cada jornada de exposición cierra con música y cultura en vivo. Los artistas se van confirmando poco a poco.',
      porRevelar: 'Artista por revelar',
      clicParaEscuchar: 'Haz clic para escuchar',
    },
    stands: {
      titulo: 'Ven y disfruta con la familia: los stands te están esperando',
      subtitulo: 'Recorre las distintas zonas de la feria y descubre lo que cada una tiene para ofrecer.',
      destacada: 'Zona destacada',
      lista: {
        mypes: {
          titulo: 'Stand MYPES',
          alt: 'Stand MYPES en la FEXPO UAP, con emprendedores atendiendo su puesto',
          descripcion: 'Las MYPES reúnen a las micro y pequeñas empresas de la región: emprendedores pandinos que muestran y venden sus productos artesanales, gastronomía y creaciones locales. Es la zona ideal para conocer el talento local, probar sabores de la tierra y llevarte algo hecho en Pando.',
        },
        empresas: {
          titulo: 'Stand Empresas',
          alt: 'Stand Empresas en la FEXPO UAP, con marcas y cooperativas atendiendo al público',
          descripcion: 'El Stand Empresas reúne a empresas, bancos y cooperativas que apuestan por el desarrollo de Pando. Aquí presentan sus servicios y propuestas directamente a la comunidad, cara a cara con quienes visitan la feria.',
        },
        profesiografico: {
          titulo: 'Stand Profesiográfico',
          alt: 'Stand Profesiográfico en la FEXPO UAP, con actividades de las carreras de la universidad',
          descripcion: 'El Stand Profesiográfico invita a conocer, explorar y elegir tu futuro: cada facultad de la Universidad Amazónica de Pando muestra sus carreras con proyectos, laboratorios y actividades en vivo, para que quienes visitan la feria descubran su vocación y decidan qué estudiar.',
        },
        artesanias: {
          titulo: 'Stand Artesanías',
          alt: 'Stand Artesanías en la FEXPO UAP, con tallados en madera y tejidos hechos a mano',
          descripcion: 'El Stand Artesanías reúne el trabajo hecho a mano de artesanos y artesanas de Pando: tallados en madera, tejidos, bisutería y piezas únicas que llevan la tradición local. Apoya el talento de la región y llévate contigo algo hecho con las manos de quienes lo crearon.',
        },
        agropecuario: {
          titulo: 'Stand Agropecuario',
          alt: 'Stand Agropecuario en la FEXPO UAP, con ganadería y producción del campo pandino',
          descripcion: 'El Stand Agropecuario muestra el trabajo del campo pandino: ganadería, producción sostenible y proyectos agrícolas de la región. Apoya a los productores locales y descubre de cerca cómo se cultiva y se cría lo que llega a tu mesa.',
        },
        vivero: {
          titulo: 'Stand Planta Viveros',
          alt: 'Stand Planta Viveros en la FEXPO UAP, con plantines y proyectos de conservación de fauna',
          descripcion: 'El Stand Planta Viveros impulsa el cuidado del medio ambiente: viveros de plantas nativas, estudios de fauna y proyectos de conservación que protegen los bosques y la biodiversidad de Pando. Súmate a sembrar un futuro más verde y sostenible para la región.',
        },
        vehicular: {
          titulo: 'Stand Vehicular',
          alt: 'Stand Vehicular en la FEXPO UAP, con camionetas, autos y motos en exhibición',
          descripcion: 'El Stand Vehicular reúne a las principales marcas y concesionarias de la región, con camionetas, autos y motos de último modelo en exhibición. Ven a descubrir las novedades del mercado automotor y conocer de cerca lo último en tecnología vehicular.',
        },
        comida: {
          titulo: 'Stand Comida',
          alt: 'Stand Comida en la FEXPO UAP, con anticuchos, salchipapas y hamburguesas recién preparados',
          descripcion: 'El Stand Comida invita a la familia a disfrutar de una gran variedad de platos, desde anticuchos y salchipapas típicos hasta hamburguesas y opciones para todos los gustos. Ven con hambre y descubre los sabores que se preparan al momento, listos para compartir.',
        },
      },
    },
    exponer: {
      titulo: 'Quiero exponer en la FEXPO UAP',
      subtitulo: 'Cuéntanos sobre ti y tu proyecto: la organización te contactará para ayudarte a reservar tu espacio.',
      nombre: 'Nombre completo',
      celular: 'Número de celular',
      empresa: 'Empresa o emprendimiento',
      rubro: 'Rubro',
      categoria: 'Categoría de stand',
      eligeCategoria: 'Elige una categoría',
      ejemploCelular: 'Ej. 71234567',
      ejemploRubro: 'Ej. GASTRONOMÍA',
      enviar: 'Enviar mi registro',
      enviando: 'Enviando…',
      errores: {
        nombre: 'Escribe tu nombre completo',
        celularVacio: 'Escribe un número de celular',
        celularInvalido: 'Escribe solo números (de 7 a 15), puede empezar con +',
        empresa: 'Escribe el nombre de tu empresa o emprendimiento',
        rubro: 'Escribe tu rubro',
        categoria: 'Elige una categoría',
      },
      okTitulo: '¡Registro enviado!',
      okMensaje: 'Nos pondremos en contacto contigo muy pronto para ayudarte a reservar tu espacio.',
      errorTitulo: 'No se pudo enviar',
      errorMensaje: 'No pudimos enviar tu registro. Revisa tu conexión e intenta de nuevo en unos minutos.',
      // El servidor explica en español qué campo rechazó: aquí se muestra tal cual.
      errorDatos: null,
      // Etiqueta bajo el número del contador de visitas (el número lo formatea la vista).
      visitas: (total) => (total === 1 ? 'visita a esta página' : 'visitas a esta página'),
    },
    pie: {
      descripcion: 'La feria de ciencia y tecnología de la Universidad Amazónica de Pando.',
      navegacion: 'Navegación',
      derechos: (anio) => `© ${anio} FEXPO UAP — Universidad Amazónica de Pando`,
      equipo: 'Equipo de Sistemas UAP',
    },
    plano: {
      dialogo: 'Plano de la feria',
      cerrarAria: 'Cerrar plano',
      alt: 'Mapa de zonas de la FEXPO UAP',
      abrirPestana: 'Abrir en nueva pestaña',
      cerrar: 'Cerrar',
    },
    estado: {
      cargando: 'Cargando información de la feria...',
      errorCarga: 'Error al cargar la información de la feria',
      reintentar: 'Reintentar',
    },
  },

  pt: {
    nav: {
      secciones: 'Seções da feira',
      inicio: 'Início',
      artistas: 'Artistas',
      stands: 'Estandes',
      obtenStand: 'Garanta seu estande',
      idioma: 'Idioma',
      temaClaro: 'Mudar para o tema claro',
      temaOscuro: 'Mudar para o tema escuro',
      abrirMenu: 'Abrir o menu de navegação',
    },
    hero: {
      comienzaEn: 'Começa em',
      dias: 'dias',
      horas: 'horas',
      min: 'min',
      seg: 'seg',
      yaComenzo: 'A FEXPO UAP já começou!',
      lugar: '18 de setembro · Cobija, Pando',
      verPlano: 'Ver o mapa da feira',
      quieroExponer: 'Quero expor',
    },
    noches: {
      titulo: 'Noites da FEXPO',
      subtitulo: 'Cada dia de exposição termina com música e cultura ao vivo. Os artistas vão sendo confirmados aos poucos.',
      porRevelar: 'Artista a ser revelado',
      clicParaEscuchar: 'Clique para ouvir',
    },
    stands: {
      titulo: 'Venha curtir com a família: os estandes estão esperando por você',
      subtitulo: 'Percorra as diferentes áreas da feira e descubra o que cada uma tem a oferecer.',
      destacada: 'Área em destaque',
      lista: {
        mypes: {
          titulo: 'Estande MYPES',
          alt: 'Estande MYPES na FEXPO UAP, com empreendedores atendendo em seus estandes',
          descripcion: 'As MYPES reúnem as micro e pequenas empresas da região: empreendedores de Pando que mostram e vendem seus produtos artesanais, sua gastronomia e suas criações locais. É a área ideal para conhecer o talento local, provar os sabores da terra e levar para casa algo feito em Pando.',
        },
        empresas: {
          titulo: 'Estande Empresas',
          alt: 'Estande Empresas na FEXPO UAP, com marcas e cooperativas atendendo o público',
          descripcion: 'O Estande Empresas reúne empresas, bancos e cooperativas que apostam no desenvolvimento de Pando. Aqui eles apresentam seus serviços e propostas diretamente à comunidade, frente a frente com quem visita a feira.',
        },
        profesiografico: {
          titulo: 'Estande de Orientação Profissional',
          alt: 'Estande de Orientação Profissional na FEXPO UAP, com atividades dos cursos da universidade',
          descripcion: 'O Estande de Orientação Profissional convida você a conhecer, explorar e escolher o seu futuro: cada faculdade da Universidade Amazônica de Pando mostra seus cursos com projetos, laboratórios e atividades ao vivo, para que quem visita a feira descubra sua vocação e decida o que estudar.',
        },
        artesanias: {
          titulo: 'Estande Artesanato',
          alt: 'Estande Artesanato na FEXPO UAP, com entalhes em madeira e tecidos feitos à mão',
          descripcion: 'O Estande Artesanato reúne o trabalho feito à mão por artesãos e artesãs de Pando: entalhes em madeira, tecidos, bijuterias e peças únicas que carregam a tradição local. Apoie o talento da região e leve com você algo feito pelas mãos de quem o criou.',
        },
        agropecuario: {
          titulo: 'Estande Agropecuário',
          alt: 'Estande Agropecuário na FEXPO UAP, com pecuária e produção do campo de Pando',
          descripcion: 'O Estande Agropecuário mostra o trabalho do campo de Pando: pecuária, produção sustentável e projetos agrícolas da região. Apoie os produtores locais e descubra de perto como se cultiva e se cria o que chega à sua mesa.',
        },
        vivero: {
          titulo: 'Estande Viveiros de Plantas',
          alt: 'Estande Viveiros de Plantas na FEXPO UAP, com mudas e projetos de conservação da fauna',
          descripcion: 'O Estande Viveiros de Plantas incentiva o cuidado com o meio ambiente: viveiros de plantas nativas, estudos da fauna e projetos de conservação que protegem as florestas e a biodiversidade de Pando. Junte-se a nós para semear um futuro mais verde e sustentável para a região.',
        },
        vehicular: {
          titulo: 'Estande Veicular',
          alt: 'Estande Veicular na FEXPO UAP, com caminhonetes, carros e motos em exposição',
          descripcion: 'O Estande Veicular reúne as principais marcas e concessionárias da região, com caminhonetes, carros e motos de último modelo em exposição. Venha descobrir as novidades do mercado automotivo e conhecer de perto o que há de mais moderno em tecnologia veicular.',
        },
        comida: {
          titulo: 'Estande Comida',
          alt: 'Estande Comida na FEXPO UAP, com anticuchos, salchipapas e hambúrgueres feitos na hora',
          descripcion: 'O Estande Comida convida a família a aproveitar uma grande variedade de pratos, desde os típicos anticuchos e salchipapas até hambúrgueres e opções para todos os gostos. Venha com fome e descubra os sabores preparados na hora, prontos para compartilhar.',
        },
      },
    },
    exponer: {
      titulo: 'Quero expor na FEXPO UAP',
      subtitulo: 'Conte-nos sobre você e seu projeto: a organização entrará em contato para ajudar você a reservar o seu espaço.',
      nombre: 'Nome completo',
      celular: 'Número de celular',
      empresa: 'Empresa ou empreendimento',
      rubro: 'Ramo de atividade',
      categoria: 'Categoria do estande',
      eligeCategoria: 'Escolha uma categoria',
      ejemploCelular: 'Ex. 71234567',
      ejemploRubro: 'Ex. GASTRONOMIA',
      enviar: 'Enviar meu cadastro',
      enviando: 'Enviando…',
      errores: {
        nombre: 'Escreva seu nome completo',
        celularVacio: 'Escreva um número de celular',
        celularInvalido: 'Escreva apenas números (de 7 a 15), pode começar com +',
        empresa: 'Escreva o nome da sua empresa ou empreendimento',
        rubro: 'Escreva seu ramo de atividade',
        categoria: 'Escolha uma categoria',
      },
      okTitulo: 'Cadastro enviado!',
      okMensaje: 'Entraremos em contato com você em breve para ajudar a reservar o seu espaço.',
      errorTitulo: 'Não foi possível enviar',
      errorMensaje: 'Não conseguimos enviar seu cadastro. Verifique sua conexão e tente novamente em alguns minutos.',
      // El servidor responde en español: en portugués se muestra este aviso en su lugar.
      errorDatos: 'Alguns dados não são válidos. Revise o formulário e tente novamente.',
      visitas: (total) => (total === 1 ? 'visita nesta página' : 'visitas nesta página'),
    },
    pie: {
      descripcion: 'A feira de ciência e tecnologia da Universidade Amazônica de Pando.',
      navegacion: 'Navegação',
      derechos: (anio) => `© ${anio} FEXPO UAP — Universidade Amazônica de Pando`,
      equipo: 'Equipe de Sistemas UAP',
    },
    plano: {
      dialogo: 'Mapa da feira',
      cerrarAria: 'Fechar o mapa',
      alt: 'Mapa das áreas da FEXPO UAP',
      abrirPestana: 'Abrir em uma nova aba',
      cerrar: 'Fechar',
    },
    estado: {
      cargando: 'Carregando as informações da feira...',
      errorCarga: 'Erro ao carregar as informações da feira',
      reintentar: 'Tentar novamente',
    },
  },
};
