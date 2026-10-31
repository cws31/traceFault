#!/usr/bin/env sh
if command -v mvn >/dev/null 2>&1; then
    exec mvn "$@"
fi
M2_WRAPPER_DIR="$HOME/.m2/wrapper/dists"
if [ -d "$M2_WRAPPER_DIR" ]; then
    MVN_EXEC=$(find "$M2_WRAPPER_DIR" -type f -name mvn 2>/dev/null | head -n 1)
    if [ -n "$MVN_EXEC" ] && [ -x "$MVN_EXEC" ]; then
        exec "$MVN_EXEC" "$@"
    fi
fi
echo "[ERROR] Maven could not be found. Please install Maven." >&2
exit 1
