"use strict";

const estado = document.querySelector("#estado");
const estadoMapa = document.querySelector("#estado-mapa");
const tabla = document.querySelector("#sitios");
const formulario = document.querySelector("#filtros");
document.querySelector("#evento").value = new URLSearchParams(location.search).get("eventoId") || "";
const colores = [[35, 117, 222], [50, 205, 154], [255, 230, 92], [217, 45, 45]];
let sitios = [];
let mapa;
let capa;
let solicitud;
let encuadrar = true;
let ultimoFiltro;

if (window.L) {
    mapa = L.map("mapa", { zoomAnimation: false }).setView([4.6097, -74.0817], 6);
    const base = L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
        maxZoom: 19,
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
    }).addTo(mapa);
    base.on("tileerror", () => {
        estadoMapa.textContent = "Mapa base incompleto. La capa de calor y la tabla siguen disponibles.";
    });
    capa = document.createElement("canvas");
    capa.className = "leaflet-layer";
    capa.setAttribute("aria-hidden", "true");
    mapa.getPanes().overlayPane.append(capa);
    mapa.on("moveend zoomend resize", dibujarCalor);
} else {
    estadoMapa.textContent = "El mapa no pudo cargarse. Consulta los registros en la tabla.";
}

formulario.addEventListener("submit", (event) => {
    event.preventDefault();
    encuadrar = true;
    cargar();
});
cargar();
setInterval(() => { if (!document.hidden) cargar(); }, 10000);

async function cargar() {
    solicitud?.abort();
    const actual = new AbortController();
    solicitud = actual;
    const evento = document.querySelector("#evento").value.trim();
    const parametros = new URLSearchParams(evento ? { eventoId: evento } : {});
    try {
        const respuesta = await fetch(`/api/campo/sitios?${parametros}`, {
            signal: actual.signal,
            cache: "no-store"
        });
        if (!respuesta.ok) throw new Error("Respuesta inválida");
        const datos = await respuesta.json();
        if (solicitud !== actual) return;
        sitios = datos;
        ultimoFiltro = evento;
        estado.textContent = sitios.length ? `${sitios.length} sitios sincronizados` :
            "Todavía no hay sitios sincronizados para esta consulta.";
        mostrarTabla();
        if (mapa && sitios.length && encuadrar) {
            mapa.fitBounds(sitios.map(sitio => [sitio.latitud, sitio.longitud]), { padding: [40, 40], maxZoom: 14 });
            encuadrar = false;
        }
        dibujarCalor();
    } catch (error) {
        if (error.name === "AbortError" || solicitud !== actual) return;
        estado.textContent = ultimoFiltro === evento
            ? "No se pudo actualizar. Se muestra la última consulta."
            : "No se pudo consultar el sistema central.";
        if (ultimoFiltro !== evento) {
            sitios = [];
            mostrarTabla();
            dibujarCalor();
        }
    }
}

function mostrarTabla() {
    tabla.replaceChildren(...sitios.map(sitio => {
        const fila = document.createElement("tr");
        for (const valor of [sitio.eventoId || "Preliminar", sitio.latitud.toFixed(5), sitio.longitud.toFixed(5), sitio.nivelAfectacion]) {
            const celda = fila.insertCell();
            celda.textContent = valor;
        }
        return fila;
    }));
}

function dibujarCalor() {
    if (!mapa) return;
    const { x: ancho, y: alto } = mapa.getSize();
    if (!ancho || !alto) return;
    capa.width = ancho;
    capa.height = alto;
    L.DomUtil.setPosition(capa, mapa.containerPointToLayerPoint([0, 0]));
    const contexto = capa.getContext("2d", { willReadFrequently: true });
    const radio = 38;
    for (const sitio of sitios) {
        const { x, y } = mapa.latLngToContainerPoint([sitio.latitud, sitio.longitud]);
        if (x < -radio || y < -radio || x > ancho + radio || y > alto + radio) continue;
        const gradiente = contexto.createRadialGradient(x, y, 0, x, y, radio);
        gradiente.addColorStop(0, `rgba(0,0,0,${sitio.nivelAfectacion / 5})`);
        gradiente.addColorStop(1, "transparent");
        contexto.fillStyle = gradiente;
        contexto.fillRect(x - radio, y - radio, radio * 2, radio * 2);
    }
    const imagen = contexto.getImageData(0, 0, ancho, alto);
    const pixeles = imagen.data;
    for (let i = 0; i < pixeles.length; i += 4) {
        const intensidad = pixeles[i + 3] / 255;
        if (!intensidad) continue;
        const escala = intensidad * (colores.length - 1);
        const tramo = Math.min(Math.floor(escala), colores.length - 2);
        for (let canal = 0; canal < 3; canal++) {
            const inicio = colores[tramo][canal];
            pixeles[i + canal] = inicio + (colores[tramo + 1][canal] - inicio) * (escala - tramo);
        }
        pixeles[i + 3] *= 0.8;
    }
    contexto.putImageData(imagen, 0, 0);
}
