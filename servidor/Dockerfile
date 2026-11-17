FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src
COPY src/Mapas.Api/Mapas.Api.csproj Mapas.Api/
RUN dotnet restore Mapas.Api/Mapas.Api.csproj
COPY src/Mapas.Api/ Mapas.Api/
RUN dotnet publish Mapas.Api/Mapas.Api.csproj -c Release -o /app --no-restore
FROM mcr.microsoft.com/dotnet/aspnet:10.0
WORKDIR /app
COPY --from=build /app .
USER $APP_UID
EXPOSE 8080
ENTRYPOINT ["dotnet", "Mapas.Api.dll"]
