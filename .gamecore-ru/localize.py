#!/usr/bin/env python3
"""Translate visible GameCore UI text without changing engine/persistence identifiers.
Automatic translations require human review; this builds a candidate release, not a
promise of perfect terminology or on-device compatibility.
"""
import functools
import html
import json
import pathlib
import re
import sys
import xml.sax.saxutils

import argostranslate.package
import argostranslate.translate

BASE = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "GameCore")
JAVA = BASE / "app/src/main/java/com/gamecore"
PLUGINS = BASE / "app/src/main/assets/plugins"
assert JAVA.is_dir(), JAVA

# Preserve common technical terms and the most frequently used UI wording.
MANUAL = {
    "About": "О приложении",
    "About this plugin": "О плагине",
    "Accessibility": "Специальные возможности",
    "Access & Tools": "Доступ и инструменты",
    "Active": "Активно",
    "Add": "Добавить",
    "Add a game": "Добавить игру",
    "Advanced": "Дополнительно",
    "Aim Lab": "Тренажёр прицеливания",
    "Apply": "Применить",
    "Back": "Назад",
    "Battery": "Аккумулятор",
    "Benchmark": "Тест производительности",
    "Brightness": "Яркость",
    "Cancel": "Отмена",
    "Change": "Изменить",
    "Check again": "Проверить ещё раз",
    "Choose a file": "Выбрать файл",
    "Clear": "Очистить",
    "Close": "Закрыть",
    "Configure": "Настроить",
    "Continue": "Продолжить",
    "CPU": "Процессор",
    "Current": "Текущее",
    "Dashboard": "Панель управления",
    "Delete": "Удалить",
    "Disable": "Отключить",
    "Dismiss": "Закрыть",
    "Done": "Готово",
    "Download": "Скачать",
    "Edit": "Изменить",
    "Enabled": "Включено",
    "Export": "Экспорт",
    "FPS": "FPS",
    "Games": "Игры",
    "GPU": "Видеоускоритель",
    "Grant": "Разрешить",
    "Graphics": "Графика",
    "Help": "Справка",
    "Hide": "Скрыть",
    "Home": "Главная",
    "Import": "Импорт",
    "Install": "Установить",
    "Keep it": "Оставить",
    "Language": "Язык",
    "Launch": "Запустить",
    "Loading": "Загрузка",
    "Manage": "Управление",
    "Memory": "Память",
    "Monitoring": "Мониторинг",
    "More": "Ещё",
    "Network": "Сеть",
    "New": "Новый",
    "No": "Нет",
    "Not now": "Не сейчас",
    "Notifications": "Уведомления",
    "OK": "ОК",
    "Open": "Открыть",
    "Open Shizuku": "Открыть Shizuku",
    "Open the setting": "Открыть настройку",
    "Options": "Параметры",
    "Overlay": "Наложение",
    "Pause": "Пауза",
    "Performance": "Производительность",
    "Permissions": "Разрешения",
    "Plugins": "Плагины",
    "Profile": "Профиль",
    "Profiles": "Профили",
    "RAM": "ОЗУ",
    "Refresh": "Обновить",
    "Remove": "Удалить",
    "Remove this plugin": "Удалить этот плагин",
    "Rename": "Переименовать",
    "Reset": "Сбросить",
    "Restore": "Восстановить",
    "Retry": "Повторить",
    "Revert": "Вернуть",
    "Save": "Сохранить",
    "Search": "Поиск",
    "Search…": "Поиск…",
    "Select": "Выбрать",
    "Session": "Сессия",
    "Set": "Установить",
    "Set up": "Настроить",
    "Settings": "Настройки",
    "Share": "Поделиться",
    "Shizuku": "Shizuku",
    "Show": "Показать",
    "Start": "Начать",
    "Start game": "Запустить игру",
    "Stop": "Остановить",
    "Stop recording": "Остановить запись",
    "Storage": "Хранилище",
    "System": "Система",
    "Temperature": "Температура",
    "Test": "Проверить",
    "Theme": "Тема",
    "Themes": "Темы",
    "Time": "Время",
    "Tools": "Инструменты",
    "Turn it on": "Включить",
    "Undo": "Отменить",
    "Update": "Обновить",
    "Use": "Использовать",
    "Use it": "Использовать",
    "Video": "Видео",
    "View history": "Посмотреть историю",
    "Volume": "Громкость",
    "Warning": "Предупреждение",
    "Yes": "Да",
}

print("Downloading English–Russian offline translation model...", flush=True)
argostranslate.package.update_package_index()
pairs = argostranslate.package.get_available_packages()
pair = next((p for p in pairs if p.from_code == "en" and p.to_code == "ru"), None)
if pair is None:
    raise RuntimeError("English–Russian Argos model is unavailable")
argostranslate.package.install_from_path(pair.download())

LANGS = {language.code: language for language in argostranslate.translate.get_installed_languages()}
ENGINE = LANGS["en"].get_translation(LANGS["ru"])

# Only replace interface prose. Leave paths, permissions, package names, JSON keys,
# settings keys, database codes and shell commands alone.
TECH = re.compile(r"(^[a-z0-9_./:-]+$|https?://|[a-z]\.[a-z]+\.[a-z]+|^\w+\.\w+$|[{}<>\\]|^@[a-z]|^--|^/)", re.I)
ASCII_WORD = re.compile(r"[A-Za-z]{2,}")
KOTLIN_TOKEN = re.compile(r"[$][{][^}]*[}]|[$][A-Za-z_]\w*|%\d+[$][sdf]|%[sdf]")
STRING = re.compile(r'"(?:[^"\\]|\\.)*"')
TRANSLATION_COUNTS = {"manual": 0, "machine": 0, "files": 0, "skipped": 0}

@functools.lru_cache(maxsize=30000)
def translate_phrase(source):
    if source in MANUAL:
        TRANSLATION_COUNTS["manual"] += 1
        return MANUAL[source]
    if not source.strip() or not ASCII_WORD.search(source):
        return source
    if TECH.search(source) or len(source) > 700 or "\n" in source:
        return source
    # Skip one-word identifiers unless they have an explicitly reviewed translation.
    if len(source.split()) == 1 and source.islower():
        return source
    tokens = {}
    def mask(match):
        key = "KEEPVARWORD" + str(len(tokens)) + "ENDE"
        tokens[key] = match.group(0)
        return key
    masked = KOTLIN_TOKEN.sub(mask, source)
    if masked.strip() in MANUAL:
        out = MANUAL[masked.strip()]
    else:
        try:
            out = ENGINE.translate(masked)
        except Exception as error:
            print("Translation error:", repr(error), flush=True)
            return source
    for key, value in tokens.items():
        out = re.sub(re.escape(key), lambda _: value, out, flags=re.I)
    # Never lose formatted variables or interpolation tokens.
    if sorted(KOTLIN_TOKEN.findall(source)) != sorted(KOTLIN_TOKEN.findall(out)):
        return source
    if not out or not re.search(r"[А-Яа-яЁё]", out):
        return source
    TRANSLATION_COUNTS["machine"] += 1
    return out

def kotlin_edit(path):
    original = path.read_text(encoding="utf-8")
    # Ignore triple-quoted raw strings and XML snippets: preserve parser semantics.
    slices = re.split(r'("""[\s\S]*?""")', original)
    did_change = False
    for part_index in range(0, len(slices), 2):
        s = slices[part_index]
        def replace(m):
            nonlocal did_change
            literal = m.group(0)
            try:
                english = json.loads(literal)
            except (json.JSONDecodeError, UnicodeDecodeError):
                return literal
            if not isinstance(english, str) or len(english) > 700:
                return literal
            # Do not touch lower-case config identifiers or values in tests.
            if not english.strip() or english in ("GameCore", "Shizuku"):
                return literal
            if english in MANUAL:
                translated = MANUAL[english]
            elif len(english.split()) < 2 and not english[:1].isupper():
                return literal
            else:
                translated = translate_phrase(english)
            if translated == english:
                return literal
            did_change = True
            return json.dumps(translated, ensure_ascii=False)
        slices[part_index] = STRING.sub(replace, s)
    updated = "".join(slices)
    if did_change and updated != original:
        path.write_text(updated, encoding="utf-8")
        TRANSLATION_COUNTS["files"] += 1

for subdir in ["ui", "aimlab"]:
    for file in sorted((JAVA / subdir).rglob("*.kt")):
        kotlin_edit(file)

# Translate bundled plugin presentation text, but never package identifiers or permissions.
DISPLAY_KEYS = {
    "name", "title", "subtitle", "description", "label", "hint",
    "placeholder", "text", "summary", "emptyText", "message",
    "body", "help", "note", "warning", "instruction", "caption",
    "header", "footer", "detail", "tooltip"
}

def translate_json(item):
    if isinstance(item, list):
        return [translate_json(x) for x in item]
    if isinstance(item, dict):
        result = {}
        for key, value in item.items():
            if key in DISPLAY_KEYS and isinstance(value, str):
                result[key] = translate_phrase(value)
            else:
                result[key] = translate_json(value)
        return result
    return item

if PLUGINS.exists():
    for file in PLUGINS.rglob("*.json"):
        original = json.loads(file.read_text(encoding="utf-8"))
        translated = translate_json(original)
        if original != translated:
            file.write_text(json.dumps(translated, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

# Use a separate Android application ID so the official app, settings and backup survive.
gradle = BASE / "app/build.gradle.kts"
g = gradle.read_text(encoding="utf-8")
if 'applicationId = "com.gamecore"' not in g:
    raise RuntimeError("Application ID declaration changed upstream")
g = g.replace('applicationId = "com.gamecore"', 'applicationId = "com.gamecore.ru"', 1)
g = g.replace('versionName = "3.18.0"', 'versionName = "3.18.0-ru"', 1)
gradle.write_text(g, encoding="utf-8")
print("Translation summary:", json.dumps(TRANSLATION_COUNTS, ensure_ascii=False), flush=True)
print("Release application ID: com.gamecore.ru", flush=True)
