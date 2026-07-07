# BSE Android

Natywna aplikacja Android (Kotlin + Jetpack Compose) czytająca kurs, ster i wiatr
z urządzenia żeglarskiego **BlueSeaEye**. Port aplikacji iOS
[`bse`](https://github.com/michaldziwisz/bse) na Androida, z naciskiem na
dostępność dla czytników ekranu (TalkBack oraz słabsze, np. Jeshuo).

## Zakres

- ekran „Ster" z odczytem kursu / steru / wiatru
- tryb czytania pełnego kursu albo odchyłki od zadanego kursu
- ogłoszenia dla czytnika ekranu albo synteza mowy (TextToSpeech)
- dźwiękowe sygnały odchyłki kursu (generowane tony)
- powiadomienie i alarm dźwiękowy przy utracie połączenia, z automatycznym
  odzyskiwaniem
- praca w tle (usługa pierwszoplanowa typu `mediaPlayback`), by odczyt i alarmy
  działały przy zgaszonym ekranie
- trwałe ustawienia i (ukryty flagą) ekran administracyjny

## Połączenie z urządzeniem

Aplikacja współpracuje bezpośrednio z urządzeniem BlueSeaEye pracującym jako
access point:

1. Na telefonie połącz się z siecią Wi-Fi `BlueSeaEye` (hasło `blueseaeye`).
2. Urządzenie udostępnia API pod bramą SoftAP `http://192.168.4.1/api`. Adres
   jest konfigurowalny w zakładce Ustawienia → sekcja „Urządzenie".
3. Aplikacja odpytuje `GET /api/helm?time=<ms>&source=<klucz>&window=<ms>`, gdzie
   `window = averageWindow * 1000` (okno uśredniania w **milisekundach**, zakres
   1000–5000). Parametr czasu nazywa się `time` (nie `t`). Kontrakt odtworzono z
   wbudowanego frontendu urządzenia.

Odpowiedź: `cgfa`/`cgf` (kurs filtrowany), `coga`/`cog` (kurs nad ziemią),
`hdga`/`hdg` (kurs kompasowy), `rsa` (wychylenie steru), `wa` (kąt do wiatru).

## Budowanie

Wymagane: JDK 17 + Android SDK (compileSdk 36).

```
./gradlew testDebugUnitTest   # testy jednostkowe logiki
./gradlew assembleDebug       # APK debug -> app/build/outputs/apk/debug/app-debug.apk
```

Repo zawiera workflow GitHub Actions `android-apk`, który uruchamia testy i buduje
APK debug jako artefakt (`BSE-android-apk`).

## Administracja

Akcje administracyjne (kalibracja żyroskopu, restart) są ukryte flagą
`FeatureFlags.ADMINISTRATION_ENABLED` w `ui/RootScreen.kt`, ponieważ bieżący
firmware zwraca dla `calibrate`/`reboot` HTTP 404. Ustaw flagę na `true`, gdy
urządzenie zacznie udostępniać te endpointy.

## Dostępność

- pola tekstowe oparte o natywny `EditText` (nazwa jako `hint` i
  `contentDescription`) — czysty Compose nie nadaje nazwy węzłowi, który fokusują
  słabsze czytniki ekranu
- przyciski i klikalne elementy mają dostępną nazwę wprost na węźle
  (`Modifier.semanticButton`)
- przełączniki i zakładki niosą `role` + `stateDescription`
- komunikaty odczytu przez region na żywo (live region) lub `TextToSpeech`
