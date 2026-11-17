using Microsoft.Maui;
using Android.App;
using Android.Content.PM;

namespace Arqui.Campo;

[Activity(
    Theme = "@style/Maui.MainTheme",
    MainLauncher = true,
    ConfigurationChanges = ConfigChanges.ScreenSize | ConfigChanges.Orientation |
        ConfigChanges.UiMode | ConfigChanges.ScreenLayout | ConfigChanges.SmallestScreenSize |
        ConfigChanges.Density)]
public sealed class MainActivity : MauiAppCompatActivity
{
}
