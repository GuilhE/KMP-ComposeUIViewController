#!/bin/bash

# DEFAULT VALUES
kmp_module="shared"
iosApp_project_folder="iosApp"
iosApp_name="iosApp"
iosApp_target_name="iosApp"
group_name="Representables"
ios_deployment_target="26"
swift_tools_version="6.2"
#####################

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$( cd "$SCRIPT_DIR/../../" && pwd )"
IOS_APP_DIR="$PROJECT_ROOT/$iosApp_project_folder"

files_source="$kmp_module/build/generated/ksp/"
spm_package_dir="$iosApp_project_folder/$group_name"
sources_dir="$spm_package_dir/Sources/$group_name"
xcodeproj_path="$iosApp_name.xcodeproj"

determine_kotlin_arch() {
  if [[ "${PLATFORM_NAME:-iphonesimulator}" == "iphoneos" ]]; then
    echo "iosArm64"
  else
    echo "iosSimulatorArm64"
  fi
}

KOTLIN_ARCH=$(determine_kotlin_arch)
BUILD_CONFIG="${CONFIGURATION:-Debug}"

# Pre-compiled Swift module interfaces produced by embedSwiftExportForXcode.
# Using pre-compiled modules instead of the SwiftExport module's source package avoids Xcode
# recompiling KMP code with a lower deployment target (causing UnsafeCurrentTask errors).
KMP_INTERFACES_ABS="$PROJECT_ROOT/$kmp_module/build/SPMBuild/$KOTLIN_ARCH/$BUILD_CONFIG/dd-interfaces"
KMP_INTERFACES_SYMLINK="$PROJECT_ROOT/$kmp_module/build/SPMBuild/SwiftInterfaces"
KMP_INTERFACES_RELPATH="../../$kmp_module/build/SPMBuild/SwiftInterfaces"

# Clang bridge modules (KotlinRuntime, KotlinRuntimeSupportBridge, etc.) live in OtherIncludes.
# Composables.swiftmodule transitively depends on these — each subdir needs its own -I flag.
KMP_OTHER_INCLUDES_ABS="$PROJECT_ROOT/$kmp_module/build/SPMPackage/$KOTLIN_ARCH/$BUILD_CONFIG/OtherIncludes"
KMP_OTHER_INCLUDES_SYMLINK="$PROJECT_ROOT/$kmp_module/build/SPMPackage/OtherIncludes"
KMP_OTHER_INCLUDES_RELPATH="../../$kmp_module/build/SPMPackage/OtherIncludes"

# ObjC export: embedAndSignAppleFrameworkForXcode writes the framework to
# build/xcode-frameworks/{CONFIGURATION}/{platform}/ (e.g. Debug/iphonesimulator26.5).
# A stable symlink avoids changing Package.swift on every build when the platform directory changes.
XCODE_FRAMEWORKS_BASE="$PROJECT_ROOT/$kmp_module/build/xcode-frameworks/$BUILD_CONFIG"
OBJC_PLATFORM_DIR=$(ls "$XCODE_FRAMEWORKS_BASE/" 2>/dev/null | grep "^${PLATFORM_NAME:-iphonesimulator}" | head -1)
KMP_OBJC_FRAMEWORK_PARENT_ABS="$XCODE_FRAMEWORKS_BASE/$OBJC_PLATFORM_DIR"
KMP_OBJC_FRAMEWORK_SYMLINK="$PROJECT_ROOT/$kmp_module/build/xcode-frameworks/current"
KMP_OBJC_FRAMEWORK_RELPATH="../../$kmp_module/build/xcode-frameworks/current"

install_gem() {
  local gem_name="$1"
  local version_constraint="$2"
  local install_output
  install_output=$(gem install "$gem_name" -v "$version_constraint" 2>&1)
  if [ $? -ne 0 ]; then
    if echo "$install_output" | grep -q "FilePermissionError\|don't have write permissions"; then
      echo "  > Permission denied. Installing to user directory..."
      install_output=$(gem install "$gem_name" -v "$version_constraint" --user-install 2>&1)
      if [ $? -eq 0 ]; then
        local user_gem_bin
        user_gem_bin="$(ruby -r rubygems -e 'puts Gem.user_dir')/bin"
        if [ -d "$user_gem_bin" ] && [[ ":$PATH:" != *":$user_gem_bin:"* ]]; then
          export PATH="$user_gem_bin:$PATH"
        fi
      else
        echo "  > ERROR: Failed to install gem even with --user-install"
        echo "$install_output"
        exit 1
      fi
    else
      echo "  > ERROR: Failed to install gem"
      echo "$install_output"
      exit 1
    fi
  fi
}

check_for_xcodeproj() {
  local min_version="1.27.0"
  local current_version
  current_version=$(gem list xcodeproj --local 2>/dev/null | grep "^xcodeproj " | sed 's/.*(\([0-9.]*\).*/\1/')
  if [ -z "$current_version" ]; then
    echo "  > Installing xcodeproj gem (minimum version: $min_version)..."
    install_gem "xcodeproj" ">= $min_version"
  else
    if [ "$(printf '%s\n' "$min_version" "$current_version" | sort -V | head -n1)" != "$min_version" ]; then
      echo "  > Updating xcodeproj gem to >= $min_version..."
      install_gem "xcodeproj" ">= $min_version"
    fi
  fi
  if ! gem spec xcodeproj > /dev/null 2>&1; then
    echo "  > ERROR: Failed to verify xcodeproj gem"
    exit 1
  fi
}

# Adds the local SPM package reference to the Xcode project if not already present.
# Uses a fast grep check to avoid invoking the xcodeproj gem on every build.
add_to_xcodeproj_if_needed() {
  local project_pbxproj="$IOS_APP_DIR/$xcodeproj_path/project.pbxproj"
  if [ ! -f "$project_pbxproj" ]; then
    echo "  > xcodeproj not found at $xcodeproj_path — skipping Xcode project setup"
    return 0
  fi

  if grep -q "XCLocalSwiftPackageReference" "$project_pbxproj" 2>/dev/null && \
     grep -q "\"$group_name\"" "$project_pbxproj" 2>/dev/null; then
    # Fast path: package reference exists. Still check for orphaned product dependencies
    # (entries without a package pointer) that cause unresolvable build errors.
    local orphaned_count
    orphaned_count=$(ruby -e '
      require "xcodeproj"
      project = Xcodeproj::Project.open(ARGV[0]) rescue exit(0)
      count = project.objects.count { |o|
        o.isa == "XCSwiftPackageProductDependency" &&
          o.respond_to?(:product_name) && o.product_name == ARGV[1] &&
          (!o.respond_to?(:package) || o.package.nil?)
      }
      puts count
    ' "$IOS_APP_DIR/$xcodeproj_path" "$group_name" 2>/dev/null || echo "0")

    if [ "${orphaned_count:-0}" -eq 0 ] 2>/dev/null; then
      return 0
    fi
    echo "  > Found $orphaned_count orphaned \"$group_name\" dependency entry/entries — cleaning up..."
  fi

  echo "  > Adding \"$group_name\" local package to Xcode project..."
  check_for_xcodeproj

  ruby_script='
    require "xcodeproj"

    xcodeproj_path  = ARGV[0]
    target_name     = ARGV[1]
    relative_path   = ARGV[2]
    product_name    = ARGV[3]

    begin
      project = Xcodeproj::Project.open(xcodeproj_path)
    rescue => e
      puts "  > ERROR: Failed to open Xcode project: #{e.message}"
      exit 1
    end

    target = project.targets.find { |t| t.name == target_name }
    unless target
      puts "  > ERROR: Target \"#{target_name}\" not found in project"
      exit 1
    end

    # Remove any orphaned product dependencies (no package pointer) left behind by
    # previous manual additions or interrupted runs, to avoid duplicate/unresolvable entries.
    orphaned = project.objects.select do |obj|
      obj.isa == "XCSwiftPackageProductDependency" &&
        obj.respond_to?(:product_name) &&
        obj.product_name == product_name &&
        (!obj.respond_to?(:package) || obj.package.nil?)
    end
    unless orphaned.empty?
      puts "  > Removing #{orphaned.size} orphaned XCSwiftPackageProductDependency entry/entries for \"#{product_name}\""
      project.targets.each do |t|
        frameworks_phase = t.frameworks_build_phase
        next unless frameworks_phase
        to_remove = frameworks_phase.files.select { |bf| bf.respond_to?(:product_ref) && orphaned.include?(bf.product_ref) }
        to_remove.each { |bf| frameworks_phase.remove_build_file(bf) }
      end
      project.targets.each do |t|
        next unless t.respond_to?(:package_product_dependencies)
        t.package_product_dependencies.delete_if { |dep| orphaned.include?(dep) }
      end
      orphaned.each(&:remove_from_project)
    end

    existing_refs = project.root_object.package_references.to_a rescue []
    pkg_ref = existing_refs.find do |ref|
      ref.isa == "XCLocalSwiftPackageReference" &&
        ref.respond_to?(:relative_path) &&
        ref.relative_path == relative_path
    end

    if pkg_ref
      already_linked = target.package_product_dependencies.any? { |dep| dep.package == pkg_ref && dep.product_name == product_name }
      if already_linked
        puts "  > Package \"#{product_name}\" already correctly linked. Skipping"
        project.save if !orphaned.empty?
        exit 0
      end
      puts "  > Package reference exists but target is not linked — re-linking \"#{product_name}\""
    else
      pkg_ref = project.new(Xcodeproj::Project::Object::XCLocalSwiftPackageReference)
      pkg_ref.relative_path = relative_path
      project.root_object.package_references << pkg_ref
    end

    pkg_dep = project.new(Xcodeproj::Project::Object::XCSwiftPackageProductDependency)
    pkg_dep.package = pkg_ref
    pkg_dep.product_name = product_name
    target.package_product_dependencies << pkg_dep

    begin
      project.save
      puts "  > Added \"#{product_name}\" local package to target \"#{target_name}\""
    rescue => e
      puts "  > ERROR: Failed to save Xcode project: #{e.message}"
      exit 1
    end
  '
  (cd "$IOS_APP_DIR" && ruby -e "$ruby_script" "$xcodeproj_path" "$iosApp_target_name" "$group_name" "$group_name")
}

# Builds the unsafeFlags array for the Representables target.
# Swift Export: -I flags pointing to pre-compiled Swift interfaces and Clang bridge modules.
# ObjC Export: -F flag pointing to the directory that contains the .framework bundle.
# The right path is chosen automatically based on what exists in the build directory.
build_unsafe_flags() {
  if [ -d "$KMP_INTERFACES_ABS" ]; then
    # Swift Export path: -I for Swift modules + -I for each Clang bridge subdir
    local flags='"-I", "'"$KMP_INTERFACES_RELPATH"'"'
    if [ -d "$KMP_OTHER_INCLUDES_ABS" ]; then
      while IFS= read -r -d '' subdir; do
        local name
        name=$(basename "$subdir")
        flags="$flags, \"-I\", \"${KMP_OTHER_INCLUDES_RELPATH}/${name}\""
      done < <(find "$KMP_OTHER_INCLUDES_ABS" -maxdepth 1 -mindepth 1 -type d -print0 | sort -z)
    fi
    echo "$flags"
  elif [ -n "$OBJC_PLATFORM_DIR" ] && [ -d "$KMP_OBJC_FRAMEWORK_PARENT_ABS" ]; then
    # ObjC Export path: -F adds the framework parent dir to the framework search path
    echo '"-F", "'"$KMP_OBJC_FRAMEWORK_RELPATH"'"'
  fi
}

# Generates a full Package.swift that uses unsafeFlags to point to the pre-compiled KMP modules.
# Avoids Xcode recompiling the KMP source package with a mismatched deployment target.
generate_package_swift() {
  local unsafe_flags
  unsafe_flags=$(build_unsafe_flags)
  cat << SWIFT_EOF
// swift-tools-version: $swift_tools_version
// Auto-generated by KMP-ComposeUIViewController — do not edit manually.

import PackageDescription

let package = Package(
    name: "$group_name",
    platforms: [
        .iOS(.v$ios_deployment_target)
    ],
    products: [
        .library(name: "$group_name", targets: ["$group_name"])
    ],
    targets: [
        .target(
            name: "$group_name",
            path: "Sources/$group_name",
            swiftSettings: [
                .unsafeFlags([$unsafe_flags])
            ]
        )
    ]
)
SWIFT_EOF
}

# Generates a stub Package.swift with no external dependency.
# Used before the first build when the KMP Swift Export package hasn't been generated yet,
# or after ./gradlew clean. Replaced automatically on the next successful Xcode build.
generate_package_swift_stub() {
  cat << SWIFT_EOF
// swift-tools-version: $swift_tools_version
// Auto-generated by KMP-ComposeUIViewController — do not edit manually.
// Stub: build the project once in Xcode to activate the full Swift Export dependency.

import PackageDescription

let package = Package(
    name: "$group_name",
    products: [
        .library(name: "$group_name", targets: ["$group_name"])
    ],
    targets: [
        .target(
            name: "$group_name",
            path: "Sources/$group_name"
        )
    ]
)
SWIFT_EOF
}

setup_spm_package() {
  mkdir -p "$sources_dir"

  local package_swift="$spm_package_dir/Package.swift"
  local new_content

  if [ -d "$KMP_INTERFACES_ABS" ]; then
    # Swift Export: stable symlink for Swift interfaces and Clang bridges
    mkdir -p "$(dirname "$KMP_INTERFACES_SYMLINK")"
    ln -sfn "$KOTLIN_ARCH/$BUILD_CONFIG/dd-interfaces" "$KMP_INTERFACES_SYMLINK"
    echo "  > Swift Export: interfaces linked → $KOTLIN_ARCH/$BUILD_CONFIG/dd-interfaces"
    if [ -d "$KMP_OTHER_INCLUDES_ABS" ]; then
      mkdir -p "$(dirname "$KMP_OTHER_INCLUDES_SYMLINK")"
      ln -sfn "$KOTLIN_ARCH/$BUILD_CONFIG/OtherIncludes" "$KMP_OTHER_INCLUDES_SYMLINK"
    fi
    new_content=$(generate_package_swift)
  elif [ -n "$OBJC_PLATFORM_DIR" ] && [ -d "$KMP_OBJC_FRAMEWORK_PARENT_ABS" ]; then
    # ObjC Export: stable symlink to the framework parent directory
    mkdir -p "$(dirname "$KMP_OBJC_FRAMEWORK_SYMLINK")"
    ln -sfn "$BUILD_CONFIG/$OBJC_PLATFORM_DIR" "$KMP_OBJC_FRAMEWORK_SYMLINK"
    echo "  > ObjC Export: framework linked → $BUILD_CONFIG/$OBJC_PLATFORM_DIR"
    new_content=$(generate_package_swift)
  else
    echo "  > KMP build output not found — generating stub Package.swift"
    echo "  > Build once in Xcode to populate the package"
    rm -f "$KMP_INTERFACES_SYMLINK"
    rm -f "$KMP_OTHER_INCLUDES_SYMLINK"
    rm -f "$KMP_OBJC_FRAMEWORK_SYMLINK"
    # SPM requires at least one source file per target. The placeholder is kept permanently
    # alongside the real generated files — removing it would cause Xcode to error with
    # "Build input file cannot be found" after having compiled it in a previous build.
    local placeholder="$sources_dir/Placeholder.swift"
    if [ ! -f "$placeholder" ]; then
      printf '// This file is intentionally kept by KMP-ComposeUIViewController.\n// It prevents the SPM "target is empty" error when Sources/ has no generated files yet.\n' > "$placeholder"
    fi
    new_content=$(generate_package_swift_stub)
  fi

  if [ -f "$package_swift" ]; then
    local existing_content
    existing_content=$(cat "$package_swift")
    if [ "$new_content" = "$existing_content" ]; then
      echo "  > Package.swift is up to date"
      return 0
    fi
    echo "  > Package.swift updated"
  else
    echo "  > Package.swift created"
  fi

  printf '%s\n' "$new_content" > "$package_swift"
}

smart_sync_files() {
  local files_source="$1"
  local files_destination="$2"

  mkdir -p "$files_destination"

  local files_copied=0
  local files_unchanged=0
  local files_removed=0
  local has_changes=0

  local source_files_list
  local source_files_map
  source_files_list=$(mktemp)
  source_files_map=$(mktemp)
  trap 'rm -f "$source_files_list" "$source_files_map"' RETURN

  while IFS= read -r -d '' source_file; do
    local filename
    local existing_file
    filename=$(basename "$source_file")
    existing_file=$(grep "^$filename|" "$source_files_map" 2>/dev/null | cut -d'|' -f2)

    if [ -z "$existing_file" ]; then
      echo "$filename|$source_file" >> "$source_files_map"
    else
      if [ "$source_file" -nt "$existing_file" ]; then
        sed -i '' "/^$filename|/d" "$source_files_map"
        echo "$filename|$source_file" >> "$source_files_map"
      fi
    fi
  done < <(find "$files_source" -type f -name '*.swift' -print0)

  local source_count
  source_count=$(wc -l < "$source_files_map" | tr -d ' ')
  echo "  > KSP output: $source_count Swift file(s) found"

  if [ ! -s "$source_files_map" ]; then
    local dest_count
    dest_count=$(find "$files_destination" -type f -name '*.swift' 2>/dev/null | wc -l | tr -d ' ')
    if [ "$dest_count" -gt 0 ]; then
      echo "  > WARNING: No Swift files found in KSP output ($files_source)."
      echo "  > Preserving existing $dest_count file(s) to avoid data loss."
      echo "  > If intentional, run: ./gradlew clean"
    else
      echo "  > Summary: 0 unchanged, 0 copied, 0 removed"
    fi
    return 0
  fi

  while IFS='|' read -r filename source_file; do
    local dest_file="$files_destination/$filename"
    echo "$filename" >> "$source_files_list"

    local should_copy=0
    if [ ! -f "$dest_file" ]; then
      should_copy=1
      echo "  > New file: $filename"
    else
      local source_md5
      local dest_md5
      source_md5=$(md5 -q "$source_file" 2>/dev/null || md5sum "$source_file" | cut -d' ' -f1)
      dest_md5=$(md5 -q "$dest_file" 2>/dev/null || md5sum "$dest_file" | cut -d' ' -f1)
      if [ "$source_md5" != "$dest_md5" ]; then
        should_copy=1
        echo "  > Modified: $filename"
      else
        files_unchanged=$((files_unchanged + 1))
      fi
    fi

    if [ $should_copy -eq 1 ]; then
      cp "$source_file" "$dest_file"
      files_copied=$((files_copied + 1))
      has_changes=1
    fi
  done < "$source_files_map"

  if [ -d "$files_destination" ]; then
    while IFS= read -r -d '' dest_file; do
      local filename
      filename=$(basename "$dest_file")
      if ! grep -Fxq "$filename" "$source_files_list"; then
        # Keep the placeholder permanently — removing it causes Xcode to error with
        # "Build input file cannot be found" because it was compiled in a previous build.
        [ "$filename" = "Placeholder.swift" ] && continue
        echo "  > Removed: $filename"
        rm -f "$dest_file"
        files_removed=$((files_removed + 1))
        has_changes=1
      fi
    done < <(find "$files_destination" -type f -name '*.swift' -print0)
  fi

  echo "  > Summary: $files_unchanged unchanged, $files_copied copied, $files_removed removed"
  return $has_changes
}

echo "  > Arch: $KOTLIN_ARCH, Config: $BUILD_CONFIG"
setup_spm_package
add_to_xcodeproj_if_needed
echo "  > Starting smart sync process"
smart_sync_files "$files_source" "$sources_dir"
echo "  > Done"