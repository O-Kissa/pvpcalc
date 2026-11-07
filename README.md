# PvPCalc v2.0.0 (Fabric, Minecraft 1.21.11, client-side)

## Jar ilman omia työkaluja (GitHub)
1. Luo tili ja uusi repo osoitteessa github.com (Add file > Upload files).
2. Lataa repoon KAIKKI tämän kansion tiedostot (myös piilokansio `.github`).
3. Avaa repon "Actions"-välilehti > "Build PvPCalc" > odota vihreää ruksia (~3 min).
4. Avaa ajo, lataa "Artifacts"-kohdasta `pvpcalc-jar` ja pura zip. Sisällä on `pvpcalc-2.0.0.jar`.

## Paikallinen build
JDK 21 + Gradle 9.2+: `gradle build` -> `build/libs/pvpcalc-2.0.0.jar`

## Lunar Client
Profiili > versio 1.21.11 > Fabric > Mods-ikkuna: lisää `pvpcalc-2.0.0.jar` ja Fabric API `0.141.1+1.21.11`.

## Komennot
/pvpcalc on | off | reset | active_kit <KIT>
/pvpcalc PvP [netherite|diamond|other|additional]
Data: config/pvpcalc/session_data.csv
