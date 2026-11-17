using Microsoft.Maui;
using Microsoft.Maui.Controls;
using Microsoft.Maui.Storage;
using Microsoft.Maui.Hosting;

namespace Arqui.Campo;

public static class MauiProgram
{
    public static MauiApp CreateMauiApp() =>
        MauiApp.CreateBuilder().UseMauiApp<CampoApp>().Build();
}

public sealed class CampoApp : Application
{
    protected override Window CreateWindow(IActivationState? activationState)
    {
        var ruta = Path.Combine(FileSystem.AppDataDirectory, "campo.db3");
        return new Window(new NavigationPage(new CapturaPage(new AlmacenCampo(ruta))));
    }
}
