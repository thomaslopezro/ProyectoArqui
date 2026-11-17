using Microsoft.Maui;
using Android.App;
using Android.Runtime;
using Microsoft.Maui.Hosting;

namespace Arqui.Campo;

[Application]
public sealed class MainApplication(nint handle, JniHandleOwnership ownership)
    : MauiApplication(handle, ownership)
{
    protected override MauiApp CreateMauiApp() => MauiProgram.CreateMauiApp();
}
