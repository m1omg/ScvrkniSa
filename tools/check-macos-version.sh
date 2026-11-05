#!/bin/bash
# Checks that every program and library in a Shrink! DMG runs on the given
# macOS version (default 12.0, Monterey) and needs nothing that is not part of
# macOS or of the app itself: the launcher, the Java runtime and the bundled
# FFmpeg. Run on a Mac:  tools/check-macos-version.sh Shrink!-1.0.0.dmg 12.0
set -euo pipefail

dmg="$1"
oldest="${2:-12.0}"

mnt="$(mktemp -d)"
work="$(mktemp -d)"
hdiutil attach -nobrowse -readonly -mountpoint "$mnt" "$dmg" > /dev/null
trap 'hdiutil detach "$mnt" > /dev/null 2>&1 || true; rm -rf "$work"' EXIT

app="$(find "$mnt" -maxdepth 1 -name '*.app' -print -quit)"
if [ -z "$app" ]; then
    echo "No .app found in $dmg" >&2
    exit 1
fi
# FFmpeg and FlatLaf's native libraries are inside jars and are unpacked when
# the app first needs them, so they are checked too.
for jar in "$app"/Contents/app/*.jar; do
    case "$jar" in
        */ffmpeg-*.jar) unzip -q -o "$jar" -d "$work/ffmpeg" ;;
        *) unzip -q -o "$jar" '*.dylib' '*.jnilib' -d "$work/$(basename "$jar")" > /dev/null 2>&1 || true ;;
    esac
done

# Prints the minimum macOS version a Mach-O file was built for.
min_version() {
    otool -l "$1" 2> /dev/null | awk '
        /LC_BUILD_VERSION/                     { build = 1 }
        build && $1 == "minos" && v == ""      { v = $2 }
        /LC_VERSION_MIN_MACOSX/                { legacy = 1 }
        legacy && $1 == "version" && v == ""   { v = $2 }
        END                                    { print v }' || true
}

# Prints the libraries a Mach-O file loads (not its own install name, which
# for a library is only an identifier).
loaded_libraries() {
    otool -l "$1" 2> /dev/null | awk '
        $1 == "cmd" { cmd = $2 }
        $1 == "name" && cmd ~ /^LC_(LOAD|LOAD_WEAK|REEXPORT|LAZY_LOAD|LOAD_UPWARD)_DYLIB$/ { print $2 }' || true
}

checked=0
too_new=0
foreign=0
report="$(mktemp)"
while IFS= read -r -d '' f; do
    case "$(file -b "$f")" in
        *Mach-O*) ;;
        *) continue ;;
    esac
    v="$(min_version "$f")"
    [ -z "$v" ] && continue
    checked=$((checked + 1))
    echo "$v ${f#"$mnt"/}" >> "$report"
    if [ "$(printf '%s\n%s\n' "$oldest" "$v" | sort -V | tail -n 1)" != "$oldest" ]; then
        echo "Needs macOS $v (newer than $oldest): ${f#"$mnt"/}"
        too_new=$((too_new + 1))
    fi
    # Every library it loads must come with macOS or with the app (no Homebrew etc.).
    while IFS= read -r ref; do
        case "$ref" in
            /usr/lib/*|/System/*|@rpath/*|@loader_path/*|@executable_path/*) ;;
            *)
                echo "Needs a library that is not part of macOS: $ref (${f#"$mnt"/})"
                foreign=$((foreign + 1))
                ;;
        esac
    done < <(loaded_libraries "$f")
done < <(find "$app" "$work" -type f -print0)

echo "Checked $checked programs and libraries. The highest minimum versions:"
sort -V -r "$report" | head -n 8 || true
rm -f "$report"
codesign -dv "$app" 2>&1 | grep -E 'Signature|Identifier' || true

if [ "$checked" -eq 0 ]; then
    echo "Found nothing to check." >&2
    exit 1
fi
if [ "$too_new" -gt 0 ] || [ "$foreign" -gt 0 ]; then
    echo "$too_new file(s) need a macOS newer than $oldest; $foreign reference(s) to outside libraries." >&2
    exit 1
fi
echo "Everything runs on macOS $oldest or newer and needs nothing outside macOS and the app."
