/**
 * ANIMACIONES DE LOGIN/LOGOUT - OASIS DIGITAL
 *
 * Este script maneja las animaciones de bienvenida y despedida.
 * Detecta los parámetros ?loginSuccess=true y ?logout=true en la URL
 * y muestra las animaciones correspondientes.
 */

document.addEventListener("DOMContentLoaded", function () {
  // Detectar parámetros en la URL
  const urlParams = new URLSearchParams(window.location.search);
  const loginSuccess = urlParams.get("loginSuccess");
  const logout = urlParams.get("logout");

  // ===== ANIMACIÓN DE LOGIN EXITOSO =====
  if (loginSuccess === "true") {
    showLoginSuccessAnimation();
    // Limpiar parámetro de la URL después de mostrar
    setTimeout(() => {
      const newUrl = window.location.pathname;
      window.history.replaceState({}, document.title, newUrl);
    }, 3000);
  }

  // ===== ANIMACIÓN DE LOGOUT =====
  if (logout === "true") {
    showLogoutAnimation();
    // Limpiar parámetro de la URL después de mostrar
    setTimeout(() => {
      const newUrl = window.location.pathname;
      window.history.replaceState({}, document.title, newUrl);
    }, 3000);
  }
});

/**
 * Muestra animación de login exitoso (fondo verde con corona)
 */
function showLoginSuccessAnimation() {
  // Crear overlay
  const overlay = document.createElement("div");
  overlay.className = "login-success-overlay";
  overlay.innerHTML = `
        <div class="login-success-content">
            <div class="crown-icon">👑</div>
            <h2 class="success-title">¡Bienvenido!</h2>
            <p class="success-message">Inicio de sesión exitoso</p>
        </div>
    `;

  // Estilos inline para la animación
  const style = document.createElement("style");
  style.textContent = `
        .login-success-overlay {
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: linear-gradient(135deg, #009B77 0%, #00c896 100%);
            display: flex;
            align-items: center;
            justify-content: center;
            z-index: 99999;
            animation: fadeIn 0.3s ease-in;
        }
        
        .login-success-content {
            text-align: center;
            animation: scaleIn 0.5s ease-out;
        }
        
        .crown-icon {
            font-size: 80px;
            animation: bounce 1s ease-in-out infinite;
            margin-bottom: 20px;
            filter: drop-shadow(0 4px 8px rgba(0,0,0,0.2));
        }
        
        .success-title {
            color: white;
            font-size: 48px;
            font-weight: 900;
            margin: 0 0 10px 0;
            text-shadow: 0 2px 4px rgba(0,0,0,0.2);
        }
        
        .success-message {
            color: rgba(255,255,255,0.9);
            font-size: 20px;
            margin: 0;
        }
        
        @keyframes fadeIn {
            from { opacity: 0; }
            to { opacity: 1; }
        }
        
        @keyframes scaleIn {
            from {
                transform: scale(0.8);
                opacity: 0;
            }
            to {
                transform: scale(1);
                opacity: 1;
            }
        }
        
        @keyframes bounce {
            0%, 100% { transform: translateY(0); }
            50% { transform: translateY(-20px); }
        }
        
        @keyframes fadeOut {
            from { opacity: 1; }
            to { opacity: 0; }
        }
    `;

  document.head.appendChild(style);
  document.body.appendChild(overlay);

  // Animar salida después de 2.5 segundos
  setTimeout(() => {
    overlay.style.animation = "fadeOut 0.5s ease-out forwards";
    setTimeout(() => {
      overlay.remove();
      style.remove();
    }, 500);
  }, 2500);
}

/**
 * Muestra animación de logout (fondo rojo)
 */
function showLogoutAnimation() {
  // Crear overlay
  const overlay = document.createElement("div");
  overlay.className = "logout-overlay";
  overlay.innerHTML = `
        <div class="logout-content">
            <div class="logout-icon">👋</div>
            <h2 class="logout-title">¡Hasta pronto!</h2>
            <p class="logout-message">Sesión cerrada correctamente</p>
        </div>
    `;

  // Estilos inline para la animación
  const style = document.createElement("style");
  style.textContent = `
        .logout-overlay {
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: linear-gradient(135deg, #D22B2B 0%, #ff4545 100%);
            display: flex;
            align-items: center;
            justify-content: center;
            z-index: 99999;
            animation: fadeIn 0.3s ease-in;
        }
        
        .logout-content {
            text-align: center;
            animation: scaleIn 0.5s ease-out;
        }
        
        .logout-icon {
            font-size: 80px;
            animation: wave 0.6s ease-in-out 3;
            margin-bottom: 20px;
            filter: drop-shadow(0 4px 8px rgba(0,0,0,0.2));
        }
        
        .logout-title {
            color: white;
            font-size: 48px;
            font-weight: 900;
            margin: 0 0 10px 0;
            text-shadow: 0 2px 4px rgba(0,0,0,0.2);
        }
        
        .logout-message {
            color: rgba(255,255,255,0.9);
            font-size: 20px;
            margin: 0;
        }
        
        @keyframes wave {
            0%, 100% { transform: rotate(0deg); }
            25% { transform: rotate(20deg); }
            75% { transform: rotate(-20deg); }
        }
    `;

  document.head.appendChild(style);
  document.body.appendChild(overlay);

  // Animar salida después de 2.5 segundos
  setTimeout(() => {
    overlay.style.animation = "fadeOut 0.5s ease-out forwards";
    setTimeout(() => {
      overlay.remove();
      style.remove();
    }, 500);
  }, 2500);
}

/**
 * Función auxiliar para mostrar notificaciones de éxito/error
 * (puede usarse en formularios)
 */
function showNotification(message, type = "success") {
  const notification = document.createElement("div");
  notification.className = `notification notification-${type}`;
  notification.textContent = message;

  const style = document.createElement("style");
  style.textContent = `
        .notification {
            position: fixed;
            top: 20px;
            right: 20px;
            padding: 16px 24px;
            border-radius: 8px;
            color: white;
            font-weight: 600;
            z-index: 10000;
            box-shadow: 0 4px 12px rgba(0,0,0,0.15);
            animation: slideInRight 0.3s ease-out, slideOutRight 0.3s ease-in 2.7s forwards;
        }
        
        .notification-success {
            background: linear-gradient(135deg, #009B77, #00c896);
        }
        
        .notification-error {
            background: linear-gradient(135deg, #D22B2B, #ff4545);
        }
        
        @keyframes slideInRight {
            from {
                transform: translateX(400px);
                opacity: 0;
            }
            to {
                transform: translateX(0);
                opacity: 1;
            }
        }
        
        @keyframes slideOutRight {
            from {
                transform: translateX(0);
                opacity: 1;
            }
            to {
                transform: translateX(400px);
                opacity: 0;
            }
        }
    `;

  document.head.appendChild(style);
  document.body.appendChild(notification);

  setTimeout(() => {
    notification.remove();
    style.remove();
  }, 3000);
}

// Exportar funciones para uso global
window.showLoginSuccessAnimation = showLoginSuccessAnimation;
window.showLogoutAnimation = showLogoutAnimation;
window.showNotification = showNotification;
