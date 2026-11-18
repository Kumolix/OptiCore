# Corelix

Свой загрузчик для клиента Minecraft Java **1.21.3**, который грузит только моды-оптимизации
(как Fabric, но без обычных модов). Цель: Pojav на Android и прирост на ПК.
Сетевой протокол не трогаем, поэтому ванильный клиент заходит по LAN как обычно.

> Статус: основа готова и проверена в CI на **настоящем клиенте 1.21.3**: ремап в имена Mojang
> (8726 классов, все проходят верификацию JVM) и Mixin, который изменяет реальный `net.minecraft.client.Minecraft`.
> В живой игре и на телефоне ещё не запускалось, самих оптимизаций пока нет (только пул потоков и монитор GC).

## Структура

| Модуль | Что это |
|---|---|
| `loader` | Загрузчик: `CorelixMain`, `CorelixClassLoader` (child-first + цепочка трансформеров), поиск и сортировка модов. Содержит API модов (`dev.corelix.api`) |
| `remap` | Ремаппер клиента: официальные маппинги Mojang (`client.txt`) на ASM, результат кэшируется |
| `mixin-support` | Сервис Mixin для Corelix (сверен с Mixin 0.17.5), собирает папку `libs` |
| `core` | Мод-ядро оптимизаций: профили телефон/ПК, конфиг, приоритетный пул потоков, монитор GC |
| `testmod` | Тестовый мод для дымового теста, в релиз не входит |

## Как это работает

1. В профиле версии (json) `mainClass` заменяется на `dev.corelix.loader.CorelixMain`.
2. Загрузчик ищет моды в `<gameDir>/mods`. Мод это jar с файлом `corelix.mod.properties`.
   Остальные jar (обычные Fabric/Forge-моды) пропускаются: это первый уровень белого списка.
3. Моды сортируются по зависимостям, вызываются их точки входа (`CorelixMod.onInit`),
   там же регистрируются трансформеры байткода.
4. Запускается ванильный `net.minecraft.client.main.Main` через `CorelixClassLoader`,
   поэтому все классы игры проходят через трансформеры.

## Папки и свойства

```
<gameDir>/mods/                  моды Corelix (corelix-core.jar и др.)
<gameDir>/corelix/libs/          corelix-remap.jar, corelix-mixin-support.jar, sponge-mixin, asm*  (или -Dcorelix.libs=...)
<gameDir>/corelix/cache/         ремапнутый клиент (создаётся при первом запуске)
```

| Свойство JVM | Назначение |
|---|---|
| `-Dcorelix.mappings=<client.txt>` | включает ремап клиента в имена Mojang (файл `client_mappings` из json версии) |
| `-Dcorelix.libs`, `-Dcorelix.cache` | папки библиотек и кэша |
| `-Dcorelix.platform=mobile\|desktop` | платформа (`pojav` понимается как `mobile`) |

Библиотеки Corelix стоят в загрузчике классов раньше classpath игры: старые копии ASM из библиотек Minecraft
иначе перекрывают нашу версию.

## Манифест мода `corelix.mod.properties`

```properties
id=my-mod              # [a-z][a-z0-9_-]{1,63}
version=1.0.0
name=My Mod
entrypoint=com.example.MyMod   # реализует dev.corelix.api.CorelixMod
depends=corelix-core           # через запятую, необязательно
mixins=my-mod.mixins.json      # конфиги Mixin, необязательно (имена классов Minecraft в именах Mojang)
api=1                          # версия API загрузчика
```

## Платформа

Профиль определяется так: `-Dcorelix.platform=mobile|desktop`, затем эвристика по окружению
(переменные Pojav, Android), иначе ПК. Лаунчеру надёжнее всего передавать свойство явно.
Профиль можно переопределить в `config/corelix.properties`.

## Сборка и проверка

```bash
gradle build            # нужен JDK 21
bash scripts/smoke.sh   # дымовой тест на поддельной игре
```

GitHub Actions делает то же самое на каждый коммит, а ещё скачивает настоящий клиент и проверяет на нём
ремап (`scripts/verify-real.sh`) и Mixin (`scripts/verify-mixin.sh`). Артефакт `corelix` содержит
`corelix-loader.jar`, `mods/corelix-core.jar` и `libs/*.jar`.

Локально без сети: `gradle build --offline -PlocalAsm=<gradle>/lib -PnoMixinSupport`.

## Дорожная карта

1. ~~Ремап клиента в имена Mojang и Mixin на настоящем 1.21.3~~ (готово, проверено в CI).
2. Интеграция в лаунчер Mobilix (подмена `mainClass`, папка libs, маппинги) и проверка на телефоне.
3. Параллельная и ленивая загрузка ресурсов.
4. Дедупликация памяти и чистка утечек при выходе из мира.
5. Планировщик меширования и загрузки чанков.
6. LOD-рендер дальних чанков по кольцам.
7. Установщик для ПК (профиль версии + libs) и поддержка других версий Minecraft.

Лицензия пока не выбрана.
