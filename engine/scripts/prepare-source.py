#!/usr/bin/env python3
"""Turn Luanti's dedicated-server executable target into an Android shared library."""

import argparse
import re
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("--bridge", type=Path, required=True)
    parser.add_argument("--library", required=True)
    args = parser.parse_args()

    cmake = args.source / "src" / "CMakeLists.txt"
    text = cmake.read_text()
    pattern = r"add_executable\(\$\{PROJECT_NAME\}server([^\n]*)\)"
    text, replacements = re.subn(
        pattern,
        r"add_library(${PROJECT_NAME}server SHARED\1)",
        text,
        count=1,
    )
    if replacements != 1:
        raise SystemExit("Unsupported Luanti server target layout")
    marker = "endif(BUILD_SERVER)"
    injection = f"""
\ttarget_sources(${{PROJECT_NAME}}server PRIVATE \"{args.bridge.as_posix()}\")
\ttarget_include_directories(${{PROJECT_NAME}}server PRIVATE \"{(args.bridge.parent.parent / 'include').as_posix()}\")
\tset_target_properties(${{PROJECT_NAME}}server PROPERTIES OUTPUT_NAME \"{args.library}\")
"""
    if marker not in text:
        raise SystemExit("Unsupported Luanti BUILD_SERVER block")
    cmake.write_text(text.replace(marker, injection + marker, 1))


if __name__ == "__main__":
    main()
