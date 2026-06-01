#!/bin/bash

# DEFAULT VALUES
kmp_module="shared"
iosApp_project_folder="iosApp"
iosApp_name="iosApp"
iosApp_target_name="iosApp"
group_name="Representables"
#####################

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$( cd "$SCRIPT_DIR/../../" && pwd )"
IOS_APP_DIR="$PROJECT_ROOT/$iosApp_project_folder"

spm_package_dir="$iosApp_project_folder/$group_name"
xcodeproj_path="$iosApp_name.xcodeproj"

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
    else
      echo "  > Using xcodeproj gem version $current_version"
    fi
  fi
  if ! gem spec xcodeproj > /dev/null 2>&1; then
    echo "  > ERROR: Failed to verify xcodeproj gem"
    exit 1
  fi
}

remove_from_xcodeproj() {
  local project_pbxproj="$IOS_APP_DIR/$xcodeproj_path/project.pbxproj"
  if [ ! -f "$project_pbxproj" ]; then
    echo "  > xcodeproj not found at $xcodeproj_path — skipping Xcode project cleanup"
    return 0
  fi

  if ! grep -q "XCLocalSwiftPackageReference" "$project_pbxproj" 2>/dev/null || \
     ! grep -q "\"$group_name\"" "$project_pbxproj" 2>/dev/null; then
    echo "  > Package \"$group_name\" not found in project. Skipping"
    return 0
  fi

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

    pkg_ref = (project.root_object.package_references.to_a rescue []).find do |ref|
      ref.isa == "XCLocalSwiftPackageReference" &&
        ref.respond_to?(:relative_path) &&
        ref.relative_path == relative_path
    end

    unless pkg_ref
      puts "  > Package \"#{product_name}\" not found in project. Skipping"
      exit 0
    end

    target.package_product_dependencies.delete_if { |dep| dep.package == pkg_ref }
    project.root_object.package_references.delete(pkg_ref)
    pkg_ref.remove_from_project

    begin
      project.save
      puts "  > Removed \"#{product_name}\" local package from target \"#{target_name}\""
    rescue => e
      puts "  > ERROR: Failed to save Xcode project: #{e.message}"
      exit 1
    end
  '
  (cd "$IOS_APP_DIR" && ruby -e "$ruby_script" "$xcodeproj_path" "$iosApp_target_name" "$group_name" "$group_name")
}

echo "  > Removing SPM package directory..."
if [ -d "$spm_package_dir" ]; then
  rm -rf "$spm_package_dir"
  echo "  > Deleted: $spm_package_dir"
else
  echo "  > Directory not found: $spm_package_dir — skipping"
fi

echo "  > Cleaning up build symlinks..."
SPM_INTERFACES_SYMLINK="$PROJECT_ROOT/$kmp_module/build/SPMBuild/SwiftInterfaces"
SPM_OTHER_INCLUDES_SYMLINK="$PROJECT_ROOT/$kmp_module/build/SPMPackage/OtherIncludes"
[ -L "$SPM_INTERFACES_SYMLINK" ] && rm -f "$SPM_INTERFACES_SYMLINK" && echo "  > Removed: SwiftInterfaces symlink"
[ -L "$SPM_OTHER_INCLUDES_SYMLINK" ] && rm -f "$SPM_OTHER_INCLUDES_SYMLINK" && echo "  > Removed: OtherIncludes symlink"

echo "  > Removing package reference from Xcode project..."
remove_from_xcodeproj

echo ""
echo "  > Done. Run setupRepresentablesSpmPackage to set up again."