<div align="center">

# 🎪 UniFex

### Sistema de Gestión Integral para Ferias Institucionales

*Controla puestos, ventas, ingresos y emprendedores de tu feria en tiempo real desde un solo lugar.*

[![Java](https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.java.com/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.x-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Thymeleaf](https://img.shields.io/badge/Thymeleaf-005F0F?style=for-the-badge&logo=thymeleaf&logoColor=white)](https://www.thymeleaf.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-316192?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![WebSocket](https://img.shields.io/badge/WebSocket-Tiempo_Real-010101?style=for-the-badge&logo=socket.io&logoColor=white)]()
[![Servidor Privado](https://img.shields.io/badge/Deploy-Servidor_Institucional-2C3E50?style=for-the-badge&logo=linux&logoColor=white)]()

[🐛 Reportar Bug](https://github.com/Srkiwi2/UniFex/issues) · [✨ Solicitar Feature](https://github.com/Srkiwi2/UniFex/issues)

</div>

---

## 📸 Capturas del Sistema

> 🖼️ *Screenshots próximamente...*

---

## 🧩 ¿Qué problema resuelve?

Organizar una feria institucional con decenas de puestos, emprendedores, ventas y flujo de personas es caótico sin las herramientas adecuadas. El registro manual genera errores, pérdida de información y falta de visibilidad en tiempo real sobre qué está pasando en la feria.

**UniFex** funciona como una **taquillería digital integral para ferias**: centraliza el registro de puestos, controla el ingreso de emprendedores, monitorea las ventas y muestra en tiempo real el estado de cada espacio de la feria.

---

## ✨ Funcionalidades Principales

### 🏪 Gestión de Puestos
- [x] Registro de puestos de venta y arquiles de la feria
- [x] Visualización **dinámica en tiempo real** de puestos disponibles y no disponibles
- [x] Asignación de puestos a empresas, negocios y emprendedores
- [x] Monitoreo del estado de cada puesto durante el evento

### 🎫 Control de Ingresos
- [x] Registro del ingreso de emprendedores a la feria
- [x] Generación de **credenciales** para cada emprendedor participante
- [x] Control de acceso al evento por emprendedor/empresa
- [x] Historial de ingresos y presencia en la feria

### 💰 Registro de Ventas y Recaudación
- [x] Registro de ventas por puesto
- [x] Control de lo recaudado por cada punto de venta
- [x] Resumen de recaudación total del evento
- [x] Reportes de ventas por puesto, emprendedor o período

### 👥 Gestión de Usuarios y Emprendedores
- [x] Registro de emprendedores, empresas y negocios participantes
- [x] Gestión de usuarios operadores del sistema (taquilleros, supervisores)
- [x] Roles diferenciados por tipo de usuario
- [x] Historial de participación de emprendedores

### ⚡ Tiempo Real con WebSocket
- [x] Mapa de puestos actualizado automáticamente sin recargar la página
- [x] Notificaciones instantáneas al asignar o liberar un puesto
- [x] Estado del evento visible para todos los operadores simultáneamente

---

## 🗺️ Mapa de Puestos en Tiempo Real

```
┌──────────────────────────────────────────────────────┐
│                  PLANO DE LA FERIA                    │
│                                                       │
│  ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐     │
│  │ P-001  │  │ P-002  │  │ P-003  │  │ P-004  │     │
│  │ ✅ LIBRE│  │ 🔴 OCUP│  │ ✅ LIBRE│  │ 🔴 OCUP│     │
│  └────────┘  └────────┘  └────────┘  └────────┘     │
│                                                       │
│  ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐     │
│  │ P-005  │  │ P-006  │  │ P-007  │  │ P-008  │     │
│  │ 🔴 OCUP│  │ ✅ LIBRE│  │ 🔴 OCUP│  │ ✅ LIBRE│     │
│  └────────┘  └────────┘  └────────┘  └────────┘     │
│                                                       │
│         Actualización automática vía WebSocket        │
└──────────────────────────────────────────────────────┘
```

---

## 🛠️ Stack Tecnológico

| Capa | Tecnología |
|------|-----------|
| **Backend** | Java 17 + Spring Boot 3 |
| **Frontend** | Thymeleaf + HTML/CSS/JS |
| **Base de Datos** | PostgreSQL |
| **Tiempo Real** | WebSocket (STOMP) |
| **Infraestructura** | Servidor privado institucional |
| **Build** | Maven |

---

## 🚀 Instalación y Ejecución Local

### Prerrequisitos

- [Java 17+](https://adoptium.net/)
- [Maven 3.8+](https://maven.apache.org/)
- [PostgreSQL 14+](https://www.postgresql.org/download/)

### Pasos

**1. Clona el repositorio**
```bash
git clone https://github.com/Srkiwi2/UniFex.git
cd UniFex
```

**2. Crea la base de datos**
```sql
CREATE DATABASE unifex;
```

**3. Configura la aplicación**

Edita `src/main/resources/application.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/unifex
spring.datasource.username=TU_USUARIO
spring.datasource.password=TU_CONTRASEÑA
spring.jpa.hibernate.ddl-auto=update
```

**4. Ejecuta el proyecto**
```bash
mvn clean install
mvn spring-boot:run
```

**5. Accede al sistema**
```
http://localhost:8080
```

---

## 📁 Estructura del Proyecto

```
UniFex/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/unifex/
│   │   │       ├── controller/      # Controladores MVC
│   │   │       ├── model/           # Entidades JPA
│   │   │       ├── repository/      # Repositorios JPA
│   │   │       ├── service/         # Lógica de negocio
│   │   │       └── websocket/       # Config. WebSocket tiempo real
│   │   └── resources/
│   │       ├── templates/
│   │       │   ├── puestos/         # Vistas del mapa de puestos
│   │       │   ├── emprendedores/   # Gestión de emprendedores
│   │       │   ├── ventas/          # Control de ventas
│   │       │   └── credenciales/    # Generación de credenciales
│   │       ├── static/              # CSS, JS, imágenes
│   │       └── application.properties
├── pom.xml
└── README.md
```

---

## 👥 Roles del Sistema

| Rol | Acceso |
|-----|--------|
| 👔 **Administrador** | Configuración completa de la feria, puestos y usuarios |
| 🎫 **Taquillero** | Registro de ingresos, asignación de puestos y credenciales |
| 💼 **Supervisor** | Monitoreo de ventas, recaudación y estado general del evento |
| 🏪 **Emprendedor** | Vista de su puesto, credencial y estado de participación |

---

## 👤 Autor

**Srkiwi2** y colega de trabajo

[![GitHub](https://img.shields.io/badge/GitHub-Srkiwi2-181717?style=for-the-badge&logo=github&logoColor=white)](https://github.com/Srkiwi2)

---

## 📄 Licencia

Desarrollado como sistema de gestión para evento institucional.
Todos los derechos reservados © 2024 Srkiwi2.

---

<div align="center">

*Organizando ferias con tecnología moderna — Bolivia 🇧🇴*

</div>
