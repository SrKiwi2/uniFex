$(function () {
    $("#formularioLogin").on("submit", function (e) {
        e.preventDefault();
        if (!this.checkValidity()) {
            $(this).addClass("was-validated");
            return;
        }

        const formData = new FormData(this);

        $.ajax({
            type: "POST",
            url: this.action,
            data: formData,
            contentType: false,
            processData: false,
            success: function (response) {
                const r = (response || "").toString().trim().toLowerCase();

                // Mapeo de respuestas → rutas
                const rutas = {
                    "inicio responsable": "/vistaR",      // ajusta si tu ruta real difiere
                    "iniciando session": "/admin",
                    "inicio vendedor": "/venta"
                };

                if (rutas[r]) {
                    // 1) Cerrar modal de login
                    $("#modalLogin").modal("hide");

                    // 2) Mostrar loader y redirigir
                    Swal.fire({
                        title: "Iniciando sesión…",
                        allowOutsideClick: false,
                        allowEscapeKey: false,
                        showConfirmButton: false,
                        didOpen: () => {
                            Swal.showLoading();
                            window.location.href = rutas[r];
                        }
                    });
                } else {
                    Swal.fire("Imposible continuar", (response || "Respuesta inesperada") + ".", "error");
                }
            },
            error: function (xhr) {
                let msg = "Ha ocurrido un error. Por favor, intenta nuevamente.";
                try {
                    const json = xhr.responseJSON || JSON.parse(xhr.responseText);
                    if (json && json.message) msg = json.message;
                } catch (e) { }
                Swal.fire("Imposible continuar", msg, "error");
            }
        });
    });
});


//FIN INICIO DE SESION

// VER CONTRASEÑA MODAL LOGIN
const togglePassword = document.getElementById("togglePassword");
const passwordInput = document.getElementById("contrasena");
const iconToggle = document.getElementById("iconToggle");

togglePassword.addEventListener("click", () => {
    const isPassword = passwordInput.getAttribute("type") === "password";
    passwordInput.setAttribute("type", isPassword ? "text" : "password");
    iconToggle.className = isPassword ? "ti ti-eye" : "ti ti-eye-off";
});