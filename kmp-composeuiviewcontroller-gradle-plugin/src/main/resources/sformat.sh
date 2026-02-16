#!/bin/bash

# DEFAULT VALUES
kmp_module="shared"
#####################

files_source="$kmp_module/build/generated/ksp/"

find_swiftFormat() {
  local possible_paths=(
    "/opt/homebrew/bin/swiftformat"
    "/usr/local/bin/swiftformat"
    "$HOME/.mint/bin/swiftformat"
    "$(which swiftformat 2>/dev/null)"
  )

  for path in "${possible_paths[@]}"; do
    if [ -n "$path" ] && [ -f "$path" ] && [ -x "$path" ]; then
      echo "$path"
      return 0
    fi
  done

  return 1
}

install_swiftFormat() {
  echo "  > SwiftFormat not found. Attempting to install..."
  if command -v brew >/dev/null 2>&1; then
    echo "  > Installing SwiftFormat via Homebrew..."
    if brew install swiftformat >/dev/null 2>&1; then
      echo "  > SwiftFormat installed successfully"
      return 0
    else
      echo "  > Failed to install via Homebrew"
    fi
  fi

  if command -v mint >/dev/null 2>&1; then
    echo "  > Installing SwiftFormat via Mint..."
    if mint install nicklockwood/SwiftFormat >/dev/null 2>&1; then
      echo "  > SwiftFormat installed successfully"
      return 0
    else
      echo "  > Failed to install via Mint"
    fi
  fi

  echo "  > Could not install SwiftFormat automatically"
  echo "  > Please install it manually using one of these commands:"
  echo "    brew install swiftformat"
  echo "    mint install nicklockwood/SwiftFormat"
  echo "  > Skipping formatting for now..."
  return 1
}

check_for_swiftFormat() {
  local swiftformat_path
  swiftformat_path=$(find_swiftFormat)

  if [ $? -ne 0 ]; then
    install_swiftFormat
    swiftformat_path=$(find_swiftFormat)
    if [ $? -ne 0 ]; then
      echo "  > SwiftFormat is not available. Skipping formatting"
      return 1
    fi
  fi

  #echo "  > Using SwiftFormat at: $swiftformat_path"
  echo "$swiftformat_path"
  return 0
}

format_swift_files() {
  local files_source="$1"

  if [ ! -d "$files_source" ]; then
    echo "  > Source directory not found: $files_source"
    echo "  > Skipping formatting"
    return 0
  fi

  local swift_files_count
  swift_files_count=$(find "$files_source" -type f -name '*.swift' 2>/dev/null | wc -l | tr -d ' ')

  if [ "$swift_files_count" -eq 0 ]; then
    echo "  > No Swift files found in $files_source"
    return 0
  fi

  echo "  > Found $swift_files_count Swift file(s) to format"

  local swiftformat_path
  swiftformat_path=$(check_for_swiftFormat)

  if [ $? -ne 0 ]; then
    # SwiftFormat not available, but we don't fail the build
    return 0
  fi

  echo "  > Running SwiftFormat..."

  if "$swiftformat_path" "$files_source" --swiftversion 6.0 2>&1 | grep -v "running swiftformat" | grep -v "^\s*$"; then
    echo "  > SwiftFormat completed successfully"
  else
    echo "  > SwiftFormat encountered some issues, but continuing with build"
  fi

  return 0
}

echo "  > Starting SwiftFormat process"
format_swift_files "$files_source"
exit 0