# Etapa 1: compila la interfaz React
FROM node:22-alpine AS construccion
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# En la plataforma integrada la interfaz usa la sesión de la plataforma y vive bajo /reconstruccion/
ARG VITE_INTEGRADO=false
ARG VITE_BASE=/
ENV VITE_INTEGRADO=$VITE_INTEGRADO VITE_BASE=$VITE_BASE
RUN npm run build

# Etapa 2: la sirve con Nginx
FROM nginx:1.27-alpine
ARG NGINX_CONF=nginx.conf
COPY ${NGINX_CONF} /etc/nginx/conf.d/default.conf
COPY --from=construccion /app/dist /usr/share/nginx/html
EXPOSE 80
